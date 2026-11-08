package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.InvitePreviewResponse;
import com.abhedyam.dto.MemberInviteRequest;
import com.abhedyam.dto.MemberResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSession;
import com.abhedyam.model.ShopMember;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import com.abhedyam.repository.ShopMemberRepository;
import com.abhedyam.util.PackageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Family members and helpers of a shop: invite (WhatsApp link), join, role, remove. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopMemberService {

    static final Duration INVITE_VALID = Duration.ofHours(48);
    static final int MAX_MEMBERS = 10; // ponytail: sanity cap; per-plan limits come with monetization
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I: read aloud on a call
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShopMemberRepository repository;
    private final OwnerRepository ownerRepository;
    private final OwnerSessionRepository sessionRepository;
    private final OwnerSessionService sessionService;
    private final FcmService fcmService;

    public static String link(String code) {
        return "https://marava.tech/join/" + code;
    }

    @Transactional
    public MemberResponse invite(UUID ownerId, MemberInviteRequest request) {
        ShopMember.Role role = role(request.getRole());
        String name = request.getName().trim();
        if (repository.findByOwnerIdAndStatusNotOrderByCreatedAtAsc(ownerId, ShopMember.Status.REMOVED).size() >= MAX_MEMBERS) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "A shop can have up to " + MAX_MEMBERS + " people. Remove someone first");
        }
        ShopMember m = new ShopMember();
        m.setOwnerId(ownerId);
        m.setName(name);
        m.setRole(role);
        m.setStatus(ShopMember.Status.INVITED);
        newCode(m);
        return toResponse(repository.save(m), Collections.emptyMap());
    }

    /** Expired or lost link: same person, new code, 48 h again. */
    @Transactional
    public MemberResponse reinvite(UUID ownerId, UUID memberId) {
        ShopMember m = own(ownerId, memberId);
        if (m.getStatus() != ShopMember.Status.INVITED) throw new BusinessException(ErrorCodes.VALIDATION_ERROR, m.getName() + " already joined");
        newCode(m);
        return toResponse(repository.save(m), Collections.emptyMap());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UUID ownerId) {
        Map<UUID, Instant> lastSeen = new HashMap<>();
        for (OwnerSession s : sessionRepository.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(ownerId)) {
            if (s.getMemberId() != null && s.getLastSeenAt() != null) lastSeen.merge(s.getMemberId(), s.getLastSeenAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return repository.findByOwnerIdAndStatusNotOrderByCreatedAtAsc(ownerId, ShopMember.Status.REMOVED).stream()
                .map(m -> toResponse(m, lastSeen)).toList();
    }

    @Transactional
    public MemberResponse changeRole(UUID ownerId, UUID memberId, String role) {
        ShopMember m = own(ownerId, memberId);
        m.setRole(role(role)); // the phone picks it up on its next token renewal (app open)
        return toResponse(repository.save(m), Collections.emptyMap());
    }

    /** Removed at once: their phones are logged out on the next request and stop getting pushes. Entries they made stay. */
    @Transactional
    public void remove(UUID ownerId, UUID memberId) {
        ShopMember m = own(ownerId, memberId);
        m.setStatus(ShopMember.Status.REMOVED);
        m.setRemovedAt(Instant.now());
        m.setInviteCode(null);
        repository.save(m);
        sessionService.revokeMember(ownerId, memberId);
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String code) {
        Optional<ShopMember> m = usableInvite(code);
        if (m.isEmpty()) return new InvitePreviewResponse(null, null, null, false);
        String shop = ownerRepository.findById(m.get().getOwnerId()).map(Owner::getBusinessName).orElse("the shop");
        return new InvitePreviewResponse(shop, m.get().getName(), m.get().getRole().name(), true);
    }

    /** The invite for this code if it can still be used (INVITED, not expired). */
    public Optional<ShopMember> usableInvite(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return repository.findByInviteCode(code.trim().toUpperCase(Locale.ROOT))
                .filter(m -> m.getStatus() == ShopMember.Status.INVITED)
                .filter(m -> m.getInviteExpiresAt() != null && m.getInviteExpiresAt().isAfter(Instant.now()));
    }

    /** An active member signed in with this Google account, if any (by uid, then email). */
    public Optional<ShopMember> activeMember(String firebaseUid, String email) {
        Optional<ShopMember> byUid = firebaseUid == null ? Optional.empty()
                : repository.findFirstByFirebaseUidAndStatus(firebaseUid, ShopMember.Status.ACTIVE);
        if (byUid.isPresent()) return byUid;
        return email == null ? Optional.empty() : repository.findFirstByEmailAndStatus(email, ShopMember.Status.ACTIVE);
    }

    /** Join: single use; the owner gets "Ravi joined your shop as Helper". */
    @Transactional
    public ShopMember accept(ShopMember invite, String firebaseUid, String email) {
        invite.setStatus(ShopMember.Status.ACTIVE);
        invite.setFirebaseUid(firebaseUid);
        invite.setEmail(email);
        invite.setJoinedAt(Instant.now());
        invite.setInviteCode(null);
        invite.setInviteExpiresAt(null);
        ShopMember m = repository.save(invite);
        try {
            fcmService.sendNotificationToUser(m.getOwnerId(), m.getName() + " joined your shop",
                    "As " + (m.getRole() == ShopMember.Role.FAMILY ? "Family" : "Helper") + ". More → Family & helpers to change or remove.",
                    PackageConstants.BUSINESS_APP_PACKAGE, Map.of("category", "MEMBER_JOINED"));
        } catch (Exception e) {
            log.warn("Member-joined push failed: {}", e.getMessage());
        }
        return m;
    }

    /** id → name for "by Ravi" (all members ever, so entries by a removed member still show who). */
    @Transactional(readOnly = true)
    public Map<UUID, String> names(UUID ownerId) {
        Map<UUID, String> names = new HashMap<>();
        repository.findByOwnerId(ownerId).forEach(m -> names.put(m.getId(), m.getName()));
        return names;
    }

    private void newCode(ShopMember m) {
        String code;
        do {
            StringBuilder b = new StringBuilder(6);
            for (int i = 0; i < 6; i++) b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = b.toString();
        } while (repository.findByInviteCode(code).isPresent());
        m.setInviteCode(code);
        m.setInviteExpiresAt(Instant.now().plus(INVITE_VALID));
    }

    private ShopMember own(UUID ownerId, UUID memberId) {
        return repository.findById(memberId)
                .filter(m -> ownerId.equals(m.getOwnerId()) && m.getStatus() != ShopMember.Status.REMOVED)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "This person is not in your shop"));
    }

    private static ShopMember.Role role(String role) {
        try {
            return ShopMember.Role.valueOf(String.valueOf(role).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Pick Family or Helper");
        }
    }

    private static MemberResponse toResponse(ShopMember m, Map<UUID, Instant> lastSeen) {
        boolean invited = m.getStatus() == ShopMember.Status.INVITED;
        boolean expired = invited && (m.getInviteExpiresAt() == null || m.getInviteExpiresAt().isBefore(Instant.now()));
        return new MemberResponse(m.getId(), m.getName(), m.getRole().name(), m.getStatus().name(),
                invited ? m.getInviteCode() : null, invited && m.getInviteCode() != null ? link(m.getInviteCode()) : null,
                m.getInviteExpiresAt(), expired, m.getEmail(), m.getJoinedAt(), lastSeen.get(m.getId()));
    }
}
