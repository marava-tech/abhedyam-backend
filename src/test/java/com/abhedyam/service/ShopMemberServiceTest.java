package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.MemberInviteRequest;
import com.abhedyam.dto.MemberResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.ShopMember;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import com.abhedyam.repository.ShopMemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Invite → join → remove. */
@ExtendWith(MockitoExtension.class)
class ShopMemberServiceTest {

    @Mock private ShopMemberRepository repo;
    @Mock private OwnerRepository ownerRepository;
    @Mock private OwnerSessionRepository sessionRepository;
    @Mock private OwnerSessionService sessionService;
    @Mock private FcmService fcmService;
    @org.mockito.Spy private NotificationText text = Texts.TEXT;
    @InjectMocks private ShopMemberService service;

    private final UUID owner = UUID.randomUUID();

    private ShopMember invited(Instant expires) {
        ShopMember m = new ShopMember();
        m.setId(UUID.randomUUID());
        m.setOwnerId(owner);
        m.setName("Ravi");
        m.setRole(ShopMember.Role.HELPER);
        m.setInviteCode("K7P2QX");
        m.setInviteExpiresAt(expires);
        return m;
    }

    @Test
    void invite_GivesReadableCodeAndLink_Valid48h() {
        when(repo.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        MemberInviteRequest req = new MemberInviteRequest();
        req.setName(" Ravi ");
        req.setRole("helper");

        MemberResponse r = service.invite(owner, req);

        assertEquals("Ravi", r.name());
        assertEquals("HELPER", r.role());
        assertTrue(r.code().matches("[A-HJ-NP-Z2-9]{6}"), r.code());
        assertEquals("https://marava.tech/join/" + r.code(), r.link());
        assertFalse(r.expired());
        assertTrue(r.inviteExpiresAt().isAfter(Instant.now().plusSeconds(47 * 3600)));
    }

    @Test
    void invite_BadRole_Refused() {
        MemberInviteRequest req = new MemberInviteRequest();
        req.setName("Ravi");
        req.setRole("ADMIN");
        assertThrows(BusinessException.class, () -> service.invite(owner, req));
    }

    @Test
    void expiredOrUsedInvite_CannotBeUsed() {
        when(repo.findByInviteCode("K7P2QX")).thenReturn(Optional.of(invited(Instant.now().minusSeconds(1))));
        assertTrue(service.usableInvite("k7p2qx").isEmpty());
        assertFalse(service.preview("K7P2QX").valid());

        ShopMember used = invited(Instant.now().plusSeconds(3600));
        used.setStatus(ShopMember.Status.ACTIVE);
        when(repo.findByInviteCode("ABCDEF")).thenReturn(Optional.of(used));
        assertTrue(service.usableInvite("ABCDEF").isEmpty());
    }

    @Test
    void accept_IsSingleUse_AndTellsTheOwner() {
        ShopMember m = invited(Instant.now().plusSeconds(3600));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.accept(m, "uid-1", "ravi@gmail.com");

        assertEquals(ShopMember.Status.ACTIVE, m.getStatus());
        assertNull(m.getInviteCode());
        verify(fcmService).sendNotificationToUser(eq(owner), org.mockito.ArgumentMatchers.<java.util.function.Function<String, String[]>>argThat(t -> t.apply("en")[0].equals("Ravi joined your shop")
            && t.apply("en")[1].contains("Helper")), anyString(), anyMap());
    }

    @Test
    void remove_LogsOutTheirPhones() {
        ShopMember m = invited(null);
        m.setStatus(ShopMember.Status.ACTIVE);
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));

        service.remove(owner, m.getId());

        assertEquals(ShopMember.Status.REMOVED, m.getStatus());
        verify(sessionService).revokeMember(owner, m.getId());
    }

    @Test
    void cannotTouchAnotherShopsMember() {
        ShopMember m = invited(null);
        m.setOwnerId(UUID.randomUUID());
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));
        assertThrows(BusinessException.class, () -> service.remove(owner, m.getId()));
        verifyNoInteractions(sessionService);
    }
}
