package com.abhedyam.service;

import com.abhedyam.dto.PaymentLinkCreateRequest;
import com.abhedyam.dto.PaymentLinkResponse;
import com.abhedyam.dto.PublicPaymentLinkResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Owner;
import com.abhedyam.model.PaymentLink;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.UPIAccount;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentLinkRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UPIAccountRepository;
import com.abhedyam.util.AuthorizationUtil;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final CustomerRepository customerRepository;
    private final OwnerRepository ownerRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final UPIAccountRepository upiAccountRepository;

    @Transactional
    public PaymentLinkResponse create(PaymentLinkCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        AuthorizationUtil.validateSameOwner(ownerId, customer.getOwnerId(), "customer");

        PaymentLink link = new PaymentLink();
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setOwnerId(ownerId);
        link.setCustomerId(customer.getId());
        link.setSaleItemId(request.getSaleItemId());
        link.setAmount(request.getAmount());
        link.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        link.setIsActive(true);
        link = paymentLinkRepository.save(link);

        PaymentLinkResponse response = new PaymentLinkResponse();
        response.setToken(link.getToken());
        response.setAmount(link.getAmount());
        response.setCustomerName(customer.getName());
        response.setExpiresAt(link.getExpiresAt());
        response.setProductName(productName(request.getSaleItemId()));
        return response;
    }

    @Transactional(readOnly = true)
    public PublicPaymentLinkResponse getPublic(String token) {
        PaymentLink link = paymentLinkRepository.findByTokenAndIsActiveTrue(token)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("LINK_EXPIRED", "This payment link has expired");
        }
        Owner owner = ownerRepository.findById(link.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        Customer customer = customerRepository.findById(link.getCustomerId()).orElse(null);

        PublicPaymentLinkResponse response = new PublicPaymentLinkResponse();
        response.setToken(link.getToken());
        response.setBusinessName(owner.getBusinessName());
        response.setOwnerPhone(owner.getPhone());
        response.setAmountDue(link.getAmount());
        if (customer != null) {
            response.setCustomerName(customer.getName());
        }
        response.setProductName(productName(link.getSaleItemId()));
        upiAccountRepository.findByOwnerId(owner.getId())
                .map(UPIAccount::getVpa)
                .ifPresent(response::setVpa);
        return response;
    }

    private String productName(UUID saleItemId) {
        if (saleItemId == null) {
            return null;
        }
        return saleItemRepository.findById(saleItemId)
                .map(SaleItem::getProductId)
                .flatMap(productRepository::findById)
                .map(Product::getName)
                .orElse(null);
    }
}
