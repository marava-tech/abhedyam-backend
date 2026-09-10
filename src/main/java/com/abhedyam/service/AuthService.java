package com.abhedyam.service;

import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.GoogleLoginRequest;
import com.abhedyam.dto.PhoneLoginRequest;
import com.abhedyam.service.GoogleOAuthService.GoogleUserInfo;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Owner;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.Subscription;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.service.interfaces.ISmsService;
import com.abhedyam.util.EmailUtil;
import com.abhedyam.util.JwtUtil;
import com.abhedyam.util.PhoneUtil;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final GoogleOAuthService googleOAuthService;
    private final FirebaseService firebaseService;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final CustomerRepository customerRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final JwtUtil jwtUtil;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final ISmsService smsService;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserInfo googleUser = googleOAuthService.verifyToken(request.getIdToken());

        String normalizedEmail = EmailUtil.normalizeEmail(googleUser.getEmail());
        if (!EmailUtil.isValidEmail(normalizedEmail)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_EMAIL",
                    "Invalid email format from Google");
        }

        Optional<User> existingUserByUid = userRepository.findByFirebaseUid(googleUser.getFirebaseUid());
        Optional<User> existingUserByEmail = userRepository.findByEmail(normalizedEmail);

        User user;
        boolean isNewUser;

        if (existingUserByUid.isPresent()) {
            user = existingUserByUid.get();
            isNewUser = false;
            if (user.getType() != UserType.BUSINESS) {
                throw new com.abhedyam.exception.BusinessException("INVALID_USER_TYPE",
                        "Google login is only available for business owners");
            }
            log.info("Owner logged in with Firebase UID: {}", googleUser.getFirebaseUid());
        } else if (existingUserByEmail.isPresent()) {
            user = existingUserByEmail.get();
            isNewUser = false;
            if (user.getType() != UserType.BUSINESS) {
                throw new com.abhedyam.exception.BusinessException("INVALID_USER_TYPE",
                        "Google login is only available for business owners");
            }
            user.setFirebaseUid(googleUser.getFirebaseUid());
            log.info("Owner logged in with email, linked Firebase UID: {}", googleUser.getFirebaseUid());
        } else {
            Owner newOwner = new Owner();
            newOwner.setName(googleUser.getName() != null && !googleUser.getName().trim().isEmpty()
                    ? googleUser.getName()
                    : "Business Owner");
            newOwner.setBusinessName(googleUser.getName() != null && !googleUser.getName().trim().isEmpty()
                    ? googleUser.getName() + "'s Business"
                    : "My Business");
            newOwner.setEmail(normalizedEmail);
            newOwner.setFirebaseUid(googleUser.getFirebaseUid());
            newOwner.setType(UserType.BUSINESS);
            newOwner.setSubscription(Subscription.GO);
            newOwner.setIsVerified(false);

            if (googleUser.getPicture() != null && !googleUser.getPicture().trim().isEmpty()) {
                newOwner.setImageUrl(googleUser.getPicture());
            }

            user = ownerRepository.save(newOwner);
            isNewUser = true;
            log.info("Created new owner account for Google login - Firebase UID: {}, email: {}",
                    googleUser.getFirebaseUid(), normalizedEmail);
        }

        if (googleUser.getName() != null && !googleUser.getName().trim().isEmpty() &&
                (user.getName() == null || user.getName().trim().isEmpty() || user.getName().equals("User"))) {
            user.setName(googleUser.getName());
        }
        if (googleUser.getPicture() != null && !googleUser.getPicture().trim().isEmpty() &&
                (user.getImageUrl() == null || user.getImageUrl().trim().isEmpty())) {
            user.setImageUrl(googleUser.getPicture());
        }
        if (user.getFirebaseUid() == null || !user.getFirebaseUid().equals(googleUser.getFirebaseUid())) {
            user.setFirebaseUid(googleUser.getFirebaseUid());
        }
        userRepository.save(user);

        String phoneForToken = user.getPhoneNormalized() != null ? user.getPhoneNormalized() : normalizedEmail;
        String token = jwtUtil.generateToken(user.getId(), phoneForToken);

        return new AuthResponse(
                token,
                user.getId().toString(),
                phoneForToken,
                user.getName(),
                isNewUser,
                UserType.BUSINESS,
                null,
                user.getCreatedAt());
    }

    @Transactional
    public AuthResponse loginWithPhone(PhoneLoginRequest request) {
        String phoneNumber = request.getPhone();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new com.abhedyam.exception.BusinessException("MISSING_PHONE",
                    "Phone number is required");
        }

        String normalizedPhone = PhoneUtil.normalizePhone(phoneNumber);
        if (!PhoneUtil.isValidPhone(normalizedPhone)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_PHONE",
                    "Invalid phone number format");
        }

        Optional<User> existingUser = userRepository.findByPhoneNormalized(normalizedPhone);

        Customer customer;
        boolean isNewUser;

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getType() != UserType.CUSTOMER) {
                throw new com.abhedyam.exception.BusinessException("INVALID_USER_TYPE",
                        "Only customer login is allowed for this app");
            }
            customer = customerRepository.findById(user.getId())
                    .orElseThrow(
                            () -> new com.abhedyam.exception.ResourceNotFoundException("Customer record not found"));
            isNewUser = false;
            log.info("Customer logged in with phone: {}", normalizedPhone);
        } else {
            customer = new Customer();
            customer.setName("Customer");
            customer.setPhone(PhoneUtil.extractPhoneWithoutCountryCode(normalizedPhone));
            customer.setPhoneNormalized(normalizedPhone);
            customer.setType(UserType.CUSTOMER);

            customer = customerRepository.save(customer);

            try {
                LocationDetails locationDetails = new LocationDetails();
                locationDetails.setUserId(customer.getId());
                locationDetails.setVillage("No Village");
                locationDetails.setLatitude(BigDecimal.ZERO);
                locationDetails.setLongitude(BigDecimal.ZERO);
                locationDetailsRepository.save(locationDetails);
            } catch (Exception e) {
                log.warn("Location details save failed for customer {}: {}", customer.getId(), e.getMessage());
            }

            isNewUser = true;
            log.info("Created new customer account for phone login - phone: {}", normalizedPhone);
        }

        String token = jwtUtil.generateCustomerToken(customer.getId(), normalizedPhone);

        return new AuthResponse(
                token,
                customer.getId().toString(),
                normalizedPhone,
                customer.getName(),
                isNewUser,
                UserType.CUSTOMER,
                customer.getOwnerId(),
                customer.getCreatedAt());
    }

    public void sendCustomerOtp(String phone) {
        String normalizedPhone = PhoneUtil.normalizePhone(phone);
        if (!PhoneUtil.isValidPhone(normalizedPhone)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_PHONE", "Invalid phone number format");
        }
        String cooldownKey = "otp:cooldown:" + normalizedPhone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new com.abhedyam.exception.BusinessException("OTP_COOLDOWN", "Wait 60 seconds before requesting another OTP");
        }
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String otpKey = "otp:" + normalizedPhone;
        redisTemplate.opsForValue().set(otpKey, otp + ":0", Duration.ofMinutes(5));
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(60));
        smsService.sendOtp(normalizedPhone, otp);
        log.info("Customer OTP issued for phone {}", normalizedPhone);
    }

    @Transactional
    public AuthResponse verifyCustomerOtp(String phone, String otp) {
        String normalizedPhone = PhoneUtil.normalizePhone(phone);
        if (!PhoneUtil.isValidPhone(normalizedPhone)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_PHONE", "Invalid phone number format");
        }
        String otpKey = "otp:" + normalizedPhone;
        String stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new com.abhedyam.exception.BusinessException("OTP_EXPIRED", "OTP expired. Request a new one");
        }
        String[] parts = stored.split(":");
        String expected = parts[0];
        int attempts = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if (attempts >= 5) {
            redisTemplate.delete(otpKey);
            throw new com.abhedyam.exception.BusinessException("OTP_LOCKED", "Too many attempts. Request a new OTP");
        }
        if (!expected.equals(otp == null ? "" : otp.trim())) {
            redisTemplate.opsForValue().set(otpKey, expected + ":" + (attempts + 1), Duration.ofMinutes(5));
            throw new com.abhedyam.exception.BusinessException("INVALID_OTP", "Incorrect OTP");
        }
        redisTemplate.delete(otpKey);
        PhoneLoginRequest request = new PhoneLoginRequest();
        request.setPhone(normalizedPhone);
        return loginWithPhone(request);
    }

    @Transactional
    public AuthResponse refresh(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_TOKEN", "Session expired. Please sign in again");
        }
        java.util.UUID userId = jwtUtil.getUserIdFromToken(token);
        String phone = jwtUtil.getPhoneFromToken(token);
        if (ownerRepository.existsById(userId)) {
            Owner owner = ownerRepository.findById(userId).orElseThrow();
            String tokenPhone = owner.getPhoneNormalized() != null ? owner.getPhoneNormalized() : owner.getEmail();
            return new AuthResponse(
                    jwtUtil.generateToken(owner.getId(), tokenPhone != null ? tokenPhone : phone),
                    owner.getId().toString(),
                    tokenPhone != null ? tokenPhone : phone,
                    owner.getName(),
                    false,
                    UserType.BUSINESS,
                    null,
                    owner.getCreatedAt());
        }
        Customer customer = customerRepository.findById(userId)
                .orElseThrow(() -> new com.abhedyam.exception.BusinessException("USER_NOT_FOUND", "Account could not be found"));
        return new AuthResponse(
                jwtUtil.generateCustomerToken(customer.getId(), customer.getPhoneNormalized()),
                customer.getId().toString(),
                customer.getPhoneNormalized(),
                customer.getName(),
                false,
                UserType.CUSTOMER,
                customer.getOwnerId(),
                customer.getCreatedAt());
    }

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.totp-secret}")
    private String adminTotpSecret;

    @Transactional
    public AuthResponse adminLogin(com.abhedyam.dto.AdminLoginRequest request) {
        if (!request.getEmail().equalsIgnoreCase(adminEmail)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_CREDENTIALS",
                    "Invalid email or OTP");
        }

        com.warrenstrange.googleauth.GoogleAuthenticator gAuth = new com.warrenstrange.googleauth.GoogleAuthenticator();
        boolean isCodeValid = gAuth.authorize(adminTotpSecret, Integer.parseInt(request.getOtp()));

        if (!isCodeValid) {
            throw new com.abhedyam.exception.BusinessException("INVALID_OTP",
                    "Invalid OTP");
        }

        // Use a fixed UUID for admin
        java.util.UUID adminId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        String token = jwtUtil.generateToken(adminId, "ADMIN");

        return new AuthResponse(
                token,
                adminId.toString(),
                "ADMIN",
                "Administrator",
                false,
                UserType.BUSINESS, // Reusing BUSINESS type for now, or could add ADMIN enum
                null,
                java.time.Instant.now());
    }
}
