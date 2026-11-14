package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.SessionResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.OwnerSession;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import com.abhedyam.util.PackageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Signed-in phones of an owner: one row per phone, its id is the JWT "sid". */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerSessionService {

    private final OwnerSessionRepository repository;
    private final FcmDetailsRepository fcmDetailsRepository;
    private final FcmService fcmService;
    private final NotificationText text;
    private final com.abhedyam.repository.ShopMemberRepository memberRepository;

    /** Login on a phone. Same phone signing in again keeps its row; a new phone tells the owner's other phones. */
    @Transactional
    public OwnerSession start(UUID ownerId, String deviceId, String deviceName) {
        return start(ownerId, null, deviceId, deviceName);
    }

    /** [memberId]: a family member / helper signing in (null = the owner). */
    @Transactional
    public OwnerSession start(UUID ownerId, UUID memberId, String deviceId, String deviceName) {
        String device = blankToNull(deviceId);
        if (device != null) {
            var existing = repository.findFirstByOwnerIdAndDeviceIdAndRevokedAtIsNull(ownerId, device);
            if (existing.isPresent() && java.util.Objects.equals(existing.get().getMemberId(), memberId)) {
                return touch(existing.get(), deviceName);
            }
            existing.ifPresent(old -> revoke(ownerId, old.getId())); // same phone, other person: old sign-in ends
        }
        boolean hasOthers = !repository.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(ownerId).isEmpty();
        OwnerSession s = new OwnerSession();
        s.setOwnerId(ownerId);
        s.setMemberId(memberId);
        s.setDeviceId(device);
        s.setDeviceName(name(deviceName));
        // signing in again on a renamed phone keeps its name
        if (device != null) repository.findFirstByOwnerIdAndDeviceIdAndLabelIsNotNullOrderByCreatedAtDesc(ownerId, device)
                .map(OwnerSession::getLabel).ifPresent(s::setLabel);
        s.setLastSeenAt(Instant.now());
        s = repository.save(s);
        if (hasOthers && memberId == null) tellOtherPhones(ownerId, s.getDeviceName()); // members: "joined" push instead
        return s;
    }

    /** Token renewal: the session must still be signed in. Old tokens without a session get one now. */
    @Transactional
    public OwnerSession renew(UUID ownerId, UUID sessionId, String deviceId, String deviceName) {
        if (sessionId == null) return start(ownerId, deviceId, deviceName);
        OwnerSession s = repository.findById(sessionId)
                .filter(x -> x.getRevokedAt() == null && ownerId.equals(x.getOwnerId()))
                .orElseThrow(() -> new BusinessException("SESSION_REVOKED", "This phone was logged out from another phone. Please sign in again"));
        return touch(s, deviceName);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID ownerId, UUID currentSessionId) {
        Map<UUID, String> names = new java.util.HashMap<>();
        memberRepository.findByOwnerId(ownerId).forEach(m -> names.put(m.getId(), m.getName()));
        return repository.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(ownerId).stream()
                .map(s -> {
                    String model = s.getDeviceName() == null ? "Phone (older app version)" : s.getDeviceName();
                    return new SessionResponse(s.getId(), s.getLabel() != null ? s.getLabel() : model,
                            s.getCreatedAt(), s.getLastSeenAt(), s.getId().equals(currentSessionId),
                            s.getMemberId() == null ? null : names.get(s.getMemberId()), s.getLabel(), model);
                })
                .toList();
    }

    /** The owner names a phone ("Ravi's phone"); blank clears it. Kept when that phone signs in again. */
    @Transactional
    public void rename(UUID ownerId, UUID sessionId, String label) {
        OwnerSession s = repository.findById(sessionId)
                .filter(x -> ownerId.equals(x.getOwnerId()) && x.getRevokedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "This phone is not signed in"));
        String l = blankToNull(label);
        if (l != null && l.length() > 40) l = l.substring(0, 40);
        s.setLabel(l);
        repository.save(s);
    }

    /** Log one phone out: its next request gets 401 and its pushes stop. */
    @Transactional
    public void revoke(UUID ownerId, UUID sessionId) {
        OwnerSession s = repository.findById(sessionId)
                .filter(x -> ownerId.equals(x.getOwnerId()))
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "This phone is not signed in"));
        if (s.getRevokedAt() != null) return;
        s.setRevokedAt(Instant.now());
        repository.save(s);
        if (s.getDeviceId() != null) fcmDetailsRepository.deleteByUserIdAndDeviceId(ownerId, s.getDeviceId());
    }

    /** A member was removed: every phone they are signed in on is logged out. */
    @Transactional
    public void revokeMember(UUID ownerId, UUID memberId) {
        for (OwnerSession s : repository.findByMemberIdAndRevokedAtIsNull(memberId)) revoke(ownerId, s.getId());
    }

    /** "Log out all other phones". Returns how many. */
    @Transactional
    public int revokeOthers(UUID ownerId, UUID currentSessionId) {
        int n = 0;
        for (OwnerSession s : repository.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(ownerId)) {
            if (s.getId().equals(currentSessionId)) continue;
            revoke(ownerId, s.getId());
            n++;
        }
        return n;
    }

    private OwnerSession touch(OwnerSession s, String deviceName) {
        s.setLastSeenAt(Instant.now());
        if (name(deviceName) != null) s.setDeviceName(name(deviceName));
        return repository.save(s);
    }

    private void tellOtherPhones(UUID ownerId, String deviceName) {
        try {
            Object phone = deviceName == null ? com.abhedyam.util.Txt.of("notif.newPhone.unknown") : deviceName;
            // sent before the new phone registers its push token, so only the other phones get it
            fcmService.sendNotificationToUser(ownerId,
                    text.byCode(com.abhedyam.util.Txt.of("notif.newPhone.title"), com.abhedyam.util.Txt.of("notif.newPhone.body", phone)),
                    PackageConstants.BUSINESS_APP_PACKAGE, Map.of("category", "NEW_DEVICE"));
        } catch (Exception e) {
            log.warn("New-phone push failed for owner {}: {}", ownerId, e.getMessage());
        }
    }

    private static String name(String deviceName) {
        String n = blankToNull(deviceName);
        return n == null ? null : n.length() > 100 ? n.substring(0, 100) : n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
