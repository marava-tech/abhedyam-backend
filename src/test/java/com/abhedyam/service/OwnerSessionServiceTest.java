package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.OwnerSession;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Signed-in phones: same phone keeps its row, a new phone tells the others, a logged-out phone can't renew. */
@ExtendWith(MockitoExtension.class)
class OwnerSessionServiceTest {

    @Mock private OwnerSessionRepository repo;
    @Mock private FcmDetailsRepository fcm;
    @Mock private FcmService fcmService;
    @InjectMocks private OwnerSessionService service;

    private final UUID owner = UUID.randomUUID();

    private OwnerSession session(String device) {
        OwnerSession s = new OwnerSession();
        s.setId(UUID.randomUUID());
        s.setOwnerId(owner);
        s.setDeviceId(device);
        return s;
    }

    @Test
    void samePhoneSigningInAgain_KeepsItsSession_NoAlert() {
        OwnerSession mine = session("dev-a");
        when(repo.findFirstByOwnerIdAndDeviceIdAndRevokedAtIsNull(owner, "dev-a")).thenReturn(Optional.of(mine));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertSame(mine, service.start(owner, "dev-a", "Vivo V2250"));
        assertEquals("Vivo V2250", mine.getDeviceName());
        verifyNoInteractions(fcmService);
    }

    @Test
    void newPhone_WithAnotherSignedIn_AlertsTheOthers() {
        when(repo.findFirstByOwnerIdAndDeviceIdAndRevokedAtIsNull(owner, "dev-b")).thenReturn(Optional.empty());
        when(repo.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(owner)).thenReturn(List.of(session("dev-a")));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(owner, "dev-b", "Redmi Note 12");

        verify(fcmService).sendNotificationToUser(eq(owner), eq("Signed in on a new phone"), contains("Redmi Note 12"), anyString(), anyMap());
    }

    @Test
    void firstPhone_NoAlert() {
        when(repo.findFirstByOwnerIdAndDeviceIdAndRevokedAtIsNull(owner, "dev-a")).thenReturn(Optional.empty());
        when(repo.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(owner)).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(owner, "dev-a", "Vivo");
        verifyNoInteractions(fcmService);
    }

    @Test
    void loggedOutPhone_CannotRenew() {
        OwnerSession gone = session("dev-a");
        gone.setRevokedAt(java.time.Instant.now());
        when(repo.findById(gone.getId())).thenReturn(Optional.of(gone));

        BusinessException e = assertThrows(BusinessException.class, () -> service.renew(owner, gone.getId(), "dev-a", null));
        assertEquals("SESSION_REVOKED", e.getErrorCode());
    }

    @Test
    void revoke_StopsThatPhonesPushes() {
        OwnerSession s = session("dev-a");
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));

        service.revoke(owner, s.getId());

        assertNotNull(s.getRevokedAt());
        verify(fcm).deleteByUserIdAndDeviceId(owner, "dev-a");
    }

    @Test
    void cannotRevokeAnotherOwnersPhone() {
        OwnerSession s = session("dev-a");
        s.setOwnerId(UUID.randomUUID());
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));

        assertThrows(BusinessException.class, () -> service.revoke(owner, s.getId()));
        verify(repo, never()).save(any());
    }
}
