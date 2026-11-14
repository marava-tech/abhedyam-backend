package com.abhedyam.service;

import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.GoogleLoginRequest;
import com.abhedyam.service.GoogleOAuthService.GoogleUserInfo;
import com.abhedyam.model.Owner;
import com.abhedyam.model.User;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.UserRepository;
import com.abhedyam.util.EmailUtil;
import com.abhedyam.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final GoogleOAuthService googleOAuthService;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final JwtUtil jwtUtil;
    private final OwnerSessionService sessionService;
    private final ShopMemberService memberService;
    private final com.abhedyam.repository.ShopMemberRepository memberRepository;

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
        } else if (memberService.activeMember(googleUser.getFirebaseUid(), normalizedEmail).isPresent()) {
            // a family member / helper signing in again (new phone, after logout): into the shop they joined
            return memberAuth(memberService.activeMember(googleUser.getFirebaseUid(), normalizedEmail).get(), deviceId, deviceName);
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
            newOwner.setAccessUntil(Instant.now().plus(BillingService.TRIAL_DAYS, ChronoUnit.DAYS));
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
        memberService.enforcePlan(user.getId()); // Shop plan: 1 phone, so the previous phone is logged out
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

    @Transactional
    public AuthResponse refresh(String token) {
        return refresh(token, null, null);
    }

    /**
     * Join a shop from an invite link. One Gmail = one shop: refused if this Gmail owns a shop or is already
     * in another one. The invite works once and for 48 h.
     */
    @Transactional
    public AuthResponse joinShop(com.abhedyam.dto.JoinShopRequest request, String deviceId, String deviceName) {
        GoogleUserInfo googleUser = googleOAuthService.verifyToken(request.getIdToken());
        String email = EmailUtil.normalizeEmail(googleUser.getEmail());
        com.abhedyam.model.ShopMember invite = memberService.usableInvite(request.getCode())
                .orElseThrow(() -> new com.abhedyam.exception.BusinessException("INVITE_INVALID",
                        "This invite has expired or was already used. Ask the shop owner to share a new one"));
        if (userRepository.findByFirebaseUid(googleUser.getFirebaseUid()).isPresent() || userRepository.findByEmail(email).isPresent()) {
            throw new com.abhedyam.exception.BusinessException("GMAIL_HAS_SHOP",
                    "This Gmail already has its own shop. Join with another Gmail");
        }
        if (memberService.activeMember(googleUser.getFirebaseUid(), email).isPresent()) {
            throw new com.abhedyam.exception.BusinessException("GMAIL_IN_SHOP",
                    "This Gmail is already in a shop. Join with another Gmail");
        }
        return memberAuth(memberService.accept(invite, googleUser.getFirebaseUid(), email), deviceId, deviceName);
    }

    private AuthResponse memberAuth(com.abhedyam.model.ShopMember member, String deviceId, String deviceName) {
        refuseIfPaused(member);
        UUID sid = sessionService.start(member.getOwnerId(), member.getId(), deviceId, deviceName).getId();
        return memberResponse(member, sid);
    }

    private static void refuseIfPaused(com.abhedyam.model.ShopMember member) {
        if (member.getPausedAt() != null) {
            throw new com.abhedyam.exception.BusinessException("PLAN_PAUSED",
                    "This shop's plan has room for fewer people now. Ask the owner to add you back");
        }
    }

    /** userId is the shop owner's id, so the app's /owners/{id}/... calls work unchanged. */
    private AuthResponse memberResponse(com.abhedyam.model.ShopMember member, UUID sessionId) {
        Owner owner = ownerRepository.findById(member.getOwnerId()).filter(o -> Boolean.TRUE.equals(o.getIsActive()))
                .orElseThrow(() -> new com.abhedyam.exception.BusinessException("SESSION_REVOKED", "This shop is closed"));
        String phone = owner.getPhoneNormalized() != null ? owner.getPhoneNormalized() : owner.getEmail();
        AuthResponse r = new AuthResponse(
                jwtUtil.generateToken(owner.getId(), phone, sessionId, member.getId(), member.getRole().name()),
                owner.getId().toString(), phone, member.getName(), false, UserType.BUSINESS, null, owner.getCreatedAt());
        r.setMemberId(member.getId());
        r.setMemberRole(member.getRole().name());
        r.setShopName(owner.getBusinessName());
        return r;
    }

    @Transactional
    public AuthResponse refresh(String token, String deviceId, String deviceName) {
        if (!jwtUtil.validateToken(token)) {
            throw new com.abhedyam.exception.BusinessException("INVALID_TOKEN", "Session expired. Please sign in again");
        }
        java.util.UUID userId = jwtUtil.getUserIdFromToken(token);
        String phone = jwtUtil.getPhoneFromToken(token);
        UUID memberId = jwtUtil.getMemberIdFromToken(token);
        if (memberId != null) {
            com.abhedyam.model.ShopMember member = memberRepository.findById(memberId)
                    .filter(m -> m.getStatus() == com.abhedyam.model.ShopMember.Status.ACTIVE && userId.equals(m.getOwnerId()))
                    .orElseThrow(() -> new com.abhedyam.exception.BusinessException("SESSION_REVOKED",
                            "You were removed from this shop. Ask the owner to add you again"));
            refuseIfPaused(member);
            UUID sid = sessionService.renew(userId, jwtUtil.getSessionIdFromToken(token), deviceId, deviceName).getId();
            return memberResponse(member, sid);
        }
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
        // Customers can't sign in (no customer app): only owners and their family/helpers renew.
        throw new com.abhedyam.exception.BusinessException("USER_NOT_FOUND", "Account could not be found");
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

        java.util.UUID adminId = com.abhedyam.security.UserPrincipal.ADMIN_ID;
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
