package com.abhedyam.service;

import com.abhedyam.config.RazorpayConfig;
import com.abhedyam.dto.PaymentVerifyRequest;
import com.abhedyam.dto.SubscriptionCreateRequest;
import com.abhedyam.dto.SubscriptionCreateResponse;
import com.abhedyam.dto.SubscriptionDetailsResponse;
import com.abhedyam.dto.SubscriptionStatusResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Subscription;
import com.abhedyam.model.enums.SubscriptionStatus;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.SubscriptionRepository;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.ISubscriptionService;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.ErrorCodes;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService implements ISubscriptionService {

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final OwnerRepository ownerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final IAuditService auditService;

    @Override
    @Transactional
    public SubscriptionCreateResponse createSubscription(SubscriptionCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        Long amount = request.getAmount();

        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCodes.INVALID_AMOUNT, "Payment amount must be greater than 0");
        }

        String razorpayMode = razorpayConfig.getMode();
        String razorpayKeyId = razorpayConfig.getKeyId();

        log.info("Creating payment order - ownerId: {}, amount: {}, razorpayMode: {}, razorpayKeyId: {}",
                ownerId, amount, razorpayMode, razorpayKeyId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        Optional<Subscription> existingPendingSubscription = subscriptionRepository
                .findFirstByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, SubscriptionStatus.PENDING);

        if (existingPendingSubscription.isPresent()) {
            Subscription pendingSubscription = existingPendingSubscription.get();
            String existingOrderId = pendingSubscription.getRazorpayOrderId();

            log.info("Reusing existing pending order - ownerId: {}, razorpayOrderId: {}",
                    ownerId, existingOrderId);

            SubscriptionCreateResponse response = new SubscriptionCreateResponse();
            response.setOrderId(existingOrderId);
            response.setAmount(amount);
            return response;
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "PRO_" + ownerId.toString().substring(0, 8));

            JSONObject notes = new JSONObject();
            notes.put("ownerId", ownerId.toString());
            notes.put("purpose", "PRO_SUBSCRIPTION");
            orderRequest.put("notes", notes);

            log.debug(
                    "Calling Razorpay API to create order - ownerId: {}, ownerName: {}, amount: {}, razorpayMode: {}, razorpayKeyId: {}",
                    ownerId, owner.getName(), amount, razorpayMode, razorpayKeyId);

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String orderId = razorpayOrder.get("id");

            log.info("Razorpay order created successfully - ownerId: {}, razorpayOrderId: {}",
                    ownerId, orderId);

            Subscription subscription = new Subscription();
            subscription.setOwnerId(ownerId);
            subscription.setRazorpayOrderId(orderId);
            subscription.setStatus(SubscriptionStatus.PENDING);

            subscriptionRepository.save(subscription);

            log.info("Order saved to database - ownerId: {}, subscriptionId: {}, razorpayOrderId: {}",
                    ownerId, subscription.getId(), orderId);

            SubscriptionCreateResponse response = new SubscriptionCreateResponse();
            response.setOrderId(orderId);
            response.setAmount(amount);
            return response;

        } catch (RazorpayException e) {
            String errorMessage = e.getMessage();
            log.error("Error creating Razorpay order - ownerId: {}, razorpayMode: {}, razorpayKeyId: {}, error: {}",
                    ownerId, razorpayMode, razorpayKeyId, errorMessage, e);
            throw new BusinessException(ErrorCodes.ORDER_CREATE_FAILED,
                    "Unable to create payment order. Please try again");
        }
    }

    @Override
    @Transactional
    public void verifyPayment(PaymentVerifyRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        log.info("Verifying payment - ownerId: {}, orderId: {}, paymentId: {}",
                ownerId, request.getOrderId(), request.getPaymentId());

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for payment verification - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        Subscription subscription = subscriptionRepository.findByRazorpayOrderId(request.getOrderId())
                .orElseThrow(() -> {
                    log.error("Order not found for payment verification - razorpayOrderId: {}, ownerId: {}",
                            request.getOrderId(), ownerId);
                    return new ResourceNotFoundException("Order could not be found");
                });

        if (!subscription.getOwnerId().equals(ownerId)) {
            log.warn(
                    "Unauthorized payment verification attempt - subscriptionOwnerId: {}, requestOwnerId: {}, orderId: {}",
                    subscription.getOwnerId(), ownerId, request.getOrderId());
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this order");
        }

        String signatureData = request.getOrderId() + "|" + request.getPaymentId();
        log.debug("Generating signature for payment verification - orderId: {}, paymentId: {}, signatureData: {}",
                request.getOrderId(), request.getPaymentId(), signatureData);

        String generatedSignature = generateSignature(signatureData, razorpayConfig.getKeySecret());
        String receivedSignature = request.getRazorpaySignature();

        if (generatedSignature == null) {
            log.error("Generated signature is null - orderId: {}, paymentId: {}, ownerId: {}",
                    request.getOrderId(), request.getPaymentId(), ownerId);
            throw new BusinessException(ErrorCodes.SIGNATURE_GENERATION_FAILED,
                    "Unable to verify payment. Please try again");
        }

        log.debug("Signature comparison - generatedLength: {}, receivedLength: {}, generated: {}, received: {}",
                generatedSignature.length(),
                receivedSignature != null ? receivedSignature.length() : 0,
                generatedSignature,
                receivedSignature);

        if (!generatedSignature.equals(receivedSignature)) {
            log.error(
                    "Invalid payment signature - orderId: {}, paymentId: {}, ownerId: {}, generatedSignature: {}, receivedSignature: {}, signatureData: {}",
                    request.getOrderId(), request.getPaymentId(), ownerId, generatedSignature, receivedSignature,
                    signatureData);
            throw new BusinessException(ErrorCodes.INVALID_SIGNATURE,
                    "Payment verification failed. Please try again");
        }

        log.debug("Signature verified successfully, fetching payment from Razorpay - paymentId: {}",
                request.getPaymentId());

        try {
            com.razorpay.Payment razorpayPayment = razorpayClient.payments.fetch(request.getPaymentId());
            Object statusObj = razorpayPayment.get("status");
            String status = statusObj != null ? statusObj.toString() : null;

            log.info("Razorpay payment status fetched - paymentId: {}, status: {}, ownerId: {}",
                    request.getPaymentId(), status, ownerId);

            if ("captured".equals(status) || "authorized".equals(status)) {
                log.info("Payment is captured/authorized, activating PRO plan - paymentId: {}, ownerId: {}",
                        request.getPaymentId(), ownerId);
                activateProPlan(owner, subscription);
            } else {
                log.warn("Payment status is not captured/authorized - paymentId: {}, status: {}, ownerId: {}",
                        request.getPaymentId(), status, ownerId);
                throw new BusinessException(ErrorCodes.PAYMENT_NOT_CAPTURED,
                        "Payment has not been completed yet. Please wait for payment confirmation");
            }
        } catch (RazorpayException e) {
            log.error("Error fetching payment from Razorpay - paymentId: {}, ownerId: {}, error: {}",
                    request.getPaymentId(), ownerId, e.getMessage(), e);
            throw new BusinessException(ErrorCodes.PAYMENT_FETCH_FAILED,
                    "Unable to verify payment. Please try again");
        }
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signature) {
        log.info("Processing Razorpay webhook - payloadLength: {}", payload != null ? payload.length() : 0);

        String generatedSignature = generateSignature(payload, razorpayConfig.getKeySecret());

        if (!generatedSignature.equals(signature)) {
            log.warn("Invalid webhook signature received - payloadLength: {}", payload != null ? payload.length() : 0);
            throw new BusinessException(ErrorCodes.INVALID_WEBHOOK_SIGNATURE, "Payment verification failed");
        }

        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");
            JSONObject payloadData = event.getJSONObject("payload");

            log.info("Webhook event received - eventType: {}", eventType);

            if ("payment.captured".equals(eventType) || "payment.authorized".equals(eventType)) {
                JSONObject paymentData = payloadData.getJSONObject("payment");
                JSONObject orderData = paymentData.getJSONObject("order");
                String orderId = orderData.getString("id");

                log.info("Payment webhook - eventType: {}, orderId: {}", eventType, orderId);

                Subscription subscription = subscriptionRepository.findByRazorpayOrderId(orderId)
                        .orElse(null);

                if (subscription == null) {
                    log.warn("Order not found for webhook - razorpayOrderId: {}, eventType: {}",
                            orderId, eventType);
                    return;
                }

                UUID ownerId = subscription.getOwnerId();
                Owner owner = ownerRepository.findById(ownerId)
                        .orElse(null);

                if (owner == null) {
                    log.warn("Owner not found for webhook - orderId: {}, ownerId: {}, eventType: {}",
                            orderId, ownerId, eventType);
                    return;
                }

                log.info("Processing payment webhook - eventType: {}, orderId: {}, ownerId: {}, ownerName: {}",
                        eventType, orderId, ownerId, owner.getName());

                activateProPlan(owner, subscription);
            } else {
                log.info("Unhandled webhook event - eventType: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing webhook - payloadLength: {}, error: {}",
                    payload != null ? payload.length() : 0, e.getMessage(), e);
            throw new BusinessException(ErrorCodes.WEBHOOK_PROCESSING_FAILED,
                    "Unable to process payment notification. Please try again");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getSubscriptionStatus() {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        log.debug("Getting subscription status - ownerId: {}", ownerId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for subscription status - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        SubscriptionStatusResponse response = new SubscriptionStatusResponse();
        response.setPlan(owner.getSubscription());
        response.setStatus(owner.getSubscriptionStatus());
        response.setValidTill(owner.getValidTill());

        log.debug("Subscription status retrieved - ownerId: {}, plan: {}, status: {}, validTill: {}",
                ownerId, owner.getSubscription(), owner.getSubscriptionStatus(), owner.getValidTill());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDetailsResponse getSubscriptionDetailsByOwnerId(UUID ownerId) {
        log.debug("Getting subscription details - ownerId: {}", ownerId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for subscription details - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        SubscriptionDetailsResponse response = new SubscriptionDetailsResponse();
        response.setOwnerId(ownerId);
        response.setPlan(owner.getSubscription());
        response.setStatus(owner.getSubscriptionStatus());
        response.setValidTill(owner.getValidTill());

        List<Subscription> subscriptions = subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        Subscription subscription = subscriptions.isEmpty() ? null : subscriptions.get(0);

        if (subscription != null) {
            response.setRazorpayOrderId(subscription.getRazorpayOrderId());
            response.setActivatedAt(subscription.getActivatedAt());
            response.setExpiredAt(subscription.getExpiredAt());
            response.setCancelledAt(subscription.getCancelledAt());

            log.debug(
                    "Subscription details retrieved - ownerId: {}, plan: {}, razorpayOrderId: {}, totalSubscriptions: {}",
                    ownerId, owner.getSubscription(), subscription.getRazorpayOrderId(), subscriptions.size());
        } else {
            log.debug("No subscription entity found for owner - ownerId: {}, plan: {}", ownerId,
                    owner.getSubscription());
        }

        return response;
    }

    public void ensureProSubscription(UUID ownerId) {
        log.debug("Checking PRO subscription requirement - ownerId: {}", ownerId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for PRO subscription check - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        boolean isValid = owner.getSubscription() == com.abhedyam.model.enums.Subscription.PRO &&
                owner.getSubscriptionStatus() == SubscriptionStatus.ACTIVE &&
                owner.getValidTill() != null &&
                !owner.getValidTill().isBefore(Instant.now());

        if (!isValid) {
            log.warn("PRO subscription check failed - ownerId: {}, plan: {}, status: {}, validTill: {}",
                    ownerId, owner.getSubscription(), owner.getSubscriptionStatus(), owner.getValidTill());
            throw new BusinessException(ErrorCodes.SUBSCRIPTION_REQUIRED,
                    "PRO subscription is required for this feature. Please upgrade to PRO plan.");
        }

        log.debug("PRO subscription check passed - ownerId: {}, validTill: {}", ownerId, owner.getValidTill());
    }

    @Override
    @Transactional
    public SubscriptionStatusResponse startTrial() {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Your account could not be found"));
        if (owner.getSubscription() == com.abhedyam.model.enums.Subscription.PRO
                && owner.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                && owner.getValidTill() != null
                && !owner.getValidTill().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCodes.TRIAL_NOT_ELIGIBLE,
                    "You already have an active PRO subscription");
        }
        if (subscriptionRepository.existsByOwnerIdAndRazorpayOrderIdStartingWith(ownerId, "TRIAL_")) {
            throw new BusinessException(ErrorCodes.TRIAL_ALREADY_USED,
                    "Free trial has already been used for this account");
        }
        Subscription subscription = new Subscription();
        subscription.setOwnerId(ownerId);
        subscription.setRazorpayOrderId("TRIAL_" + ownerId);
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscriptionRepository.save(subscription);
        Instant validTill = Instant.now().plus(45, ChronoUnit.DAYS);
        activateProPlanWithValidTill(owner, subscription, validTill);
        log.info("45-day free trial started - ownerId: {}, validTill: {}", ownerId, validTill);
        return getSubscriptionStatus();
    }

    @Override
    @Transactional
    public void activateProPlanForTesting(UUID ownerId) {
        log.warn("Activating PRO plan for testing - ownerId: {}", ownerId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for test PRO activation - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        List<Subscription> subscriptions = subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        Subscription subscription;

        if (subscriptions.isEmpty()) {
            log.info("No existing subscriptions found, creating new test subscription - ownerId: {}", ownerId);
            subscription = new Subscription();
            subscription.setOwnerId(ownerId);
            subscription.setRazorpayOrderId(
                    "test_order_" + ownerId.toString().substring(0, 8) + "_" + System.currentTimeMillis());
            subscription.setStatus(SubscriptionStatus.PENDING);
        } else {
            subscription = subscriptions.get(0);
            log.info(
                    "Using most recent subscription for testing - ownerId: {}, subscriptionId: {}, razorpayOrderId: {}",
                    ownerId, subscription.getId(), subscription.getRazorpayOrderId());
        }

        activateProPlan(owner, subscription);

        log.warn("PRO plan activated for testing - ownerId: {}, validTill: {}", ownerId, owner.getValidTill());
    }

    @Override
    @Transactional
    public void downgradeToGoForTesting(UUID ownerId) {
        log.warn("Downgrading to GO plan for testing - ownerId: {}", ownerId);

        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> {
                    log.error("Owner not found for test GO downgrade - ownerId: {}", ownerId);
                    return new ResourceNotFoundException("Your account could not be found");
                });

        List<Subscription> subscriptions = subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        Subscription subscription = subscriptions.isEmpty() ? null : subscriptions.get(0);

        if (subscription != null) {
            log.info(
                    "Using most recent subscription for testing - ownerId: {}, subscriptionId: {}, razorpayOrderId: {}",
                    ownerId, subscription.getId(), subscription.getRazorpayOrderId());
        } else {
            log.info("No existing subscriptions found for testing - ownerId: {}", ownerId);
        }

        String oldPlan = owner.getSubscription() != null ? owner.getSubscription().toString() : null;
        String oldStatus = owner.getSubscriptionStatus() != null ? owner.getSubscriptionStatus().toString() : null;
        String subscriptionId = subscription != null ? subscription.getRazorpayOrderId() : null;

        owner.setSubscription(com.abhedyam.model.enums.Subscription.GO);
        owner.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
        owner.setValidTill(null);
        owner.setSubscriptionId(null);

        if (subscription != null) {
            Instant now = Instant.now();
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setExpiredAt(now);
            subscriptionRepository.save(subscription);
        }

        // Rollback free trial for easier testing
        subscriptionRepository.deleteAllByOwnerIdAndRazorpayOrderIdStartingWith(ownerId, "TRIAL_");

        ownerRepository.save(owner);

        auditService.logSubscriptionChange(
                ownerId,
                oldPlan,
                "GO",
                oldStatus,
                "EXPIRED",
                subscriptionId,
                null);

        log.warn("GO plan activated for testing - ownerId: {}, plan: {}", ownerId, owner.getSubscription());
    }

    private void activateProPlan(Owner owner, Subscription subscription) {
        Instant now = Instant.now();
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        Instant validTill = zonedDateTime.plus(1, ChronoUnit.YEARS).toInstant();
        activateProPlanWithValidTill(owner, subscription, validTill);
    }

    private void activateProPlanWithValidTill(Owner owner, Subscription subscription, Instant validTill) {
        UUID ownerId = owner.getId();
        String razorpayOrderId = subscription.getRazorpayOrderId();

        log.info("Activating PRO plan - ownerId: {}, razorpayOrderId: {}, validTill: {}",
                ownerId, razorpayOrderId, validTill);

        String oldPlan = owner.getSubscription() != null ? owner.getSubscription().toString() : null;
        String oldStatus = owner.getSubscriptionStatus() != null ? owner.getSubscriptionStatus().toString() : null;

        owner.setSubscription(com.abhedyam.model.enums.Subscription.PRO);
        owner.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        owner.setSubscriptionId(razorpayOrderId);
        owner.setValidTill(validTill);

        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setActivatedAt(now);
        subscription.setValidTill(validTill);

        ownerRepository.save(owner);
        subscriptionRepository.save(subscription);

        auditService.logSubscriptionChange(
                ownerId,
                oldPlan,
                "PRO",
                oldStatus,
                "ACTIVE",
                razorpayOrderId,
                validTill);

        log.info("PRO plan activated successfully - ownerId: {}, razorpayOrderId: {}, validTill: {}",
                ownerId, razorpayOrderId, validTill);
    }

    private String generateSignature(String data, String secret) {
        try {
            if (data == null) {
                log.error("Cannot generate signature: data is null");
                throw new BusinessException(ErrorCodes.SIGNATURE_GENERATION_FAILED,
                        "Data cannot be null for signature generation");
            }
            log.debug("Generating HMAC SHA256 signature - dataLength: {}", data.length());
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error generating signature - dataLength: {}, error: {}",
                    data != null ? data.length() : 0, e.getMessage(), e);
            throw new BusinessException(ErrorCodes.SIGNATURE_GENERATION_FAILED,
                    "Unable to verify payment. Please try again");
        }
    }
}
