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
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.util.EmailUtil;
import com.abhedyam.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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
    private final OwnerSessionService sessionService;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        return loginWithGoogle(request, null, null);
    }

    /** [deviceId]/[deviceName]: X-Device-Id / X-Device-Name of the phone signing in (null from older apps). */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request, String deviceId, String deviceName) {
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
        UUID sessionId = sessionService.start(user.getId(), deviceId, deviceName).getId();
        String token = jwtUtil.generateToken(user.getId(), phoneForToken, sessionId);

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

    /**
     * Disabled: this issued a customer token from a phone number alone (no proof of
     * ownership), so anyone could sign in as any customer. Customer sign-in stays off until a
     * replacement (owner-issued access code) ships.
     */
    @Transactional
    public AuthResponse loginWithPhone(PhoneLoginRequest request) {
        throw new com.abhedyam.exception.BusinessException("OTP_REQUIRED",
                "Verify your phone number to sign in");
    }

    @Transactional
    public AuthResponse refresh(String token) {
        return refresh(token, null, null);
    }

    @Transactional
    public AuthResponse refresh(String token, String deviceId, String deviceName) {
        if (!jwtUtil.validateToken(token)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_TOKEN", "Session expired. Please sign in again");
        }
        java.util.UUID userId = jwtUtil.getUserIdFromToken(token);
        String phone = jwtUtil.getPhoneFromToken(token);
        if (ownerRepository.existsById(userId)) {
            Owner owner = ownerRepository.findById(userId).orElseThrow();
            String tokenPhone = owner.getPhoneNormalized() != null ? owner.getPhoneNormalized() : owner.getEmail();
            // logged out from another phone → refused, so the app goes to sign in
            UUID sessionId = sessionService.renew(owner.getId(), jwtUtil.getSessionIdFromToken(token), deviceId, deviceName).getId();
            return new AuthResponse(
                    jwtUtil.generateToken(owner.getId(), tokenPhone != null ? tokenPhone : phone, sessionId),
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
