package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.abhedyam.model.FcmDetails;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSession;
import com.abhedyam.repository.FcmDetailsRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSessionRepository;
import com.abhedyam.util.PackageConstants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Business plan: a shop's push also reaches the owner's phones open in their other shops, never a helper's. */
class FcmOtherShopsTest {

    private final FcmDetailsRepository tokens = mock(FcmDetailsRepository.class);
    private final OwnerRepository owners = mock(OwnerRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    private final OwnerSessionRepository sessions = mock(OwnerSessionRepository.class);
    private final FcmService fcm = new FcmService(tokens, owners, sessions);

    private Owner shop(UUID parent, String product) {
        Owner o = new Owner();
        o.setId(UUID.randomUUID());
        o.setIsActive(true);
        o.setParentOwnerId(parent);
        o.setAccessUntil(parent == null ? Instant.now().plusSeconds(86400) : null);
        o.setPlayProductId(product);
        when(owners.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private FcmDetails token(Owner shop, String device) {
        FcmDetails d = new FcmDetails();
        d.setUserId(shop.getId());
        d.setDeviceId(device);
        d.setToken("t-" + device);
        return d;
    }

    private OwnerSession session(String device, UUID member) {
        OwnerSession s = new OwnerSession();
        s.setDeviceId(device);
        s.setMemberId(member);
        return s;
    }

    @Test
    void ownersPhonesInOtherShopsOnly_notHelpers_notTheShopItself() {
        Owner main = shop(null, "abhedyam_business:yearly");
        Owner branch = shop(main.getId(), null);
        when(owners.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())).thenReturn(List.of(branch));
        // Owner's 2nd phone is open in the branch; a helper works there too.
        when(sessions.findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(branch.getId()))
                .thenReturn(List.of(session("owner-phone-2", null), session("helper-phone", UUID.randomUUID())));
        FcmDetails ownerPhone = token(branch, "owner-phone-2");
        when(tokens.findByUserIdAndPackageName(branch.getId(), PackageConstants.BUSINESS_APP_PACKAGE))
                .thenReturn(List.of(ownerPhone, token(branch, "helper-phone")));

        assertEquals(List.of(ownerPhone), fcm.otherShopPhones(main)); // a push for the main shop
        verify(sessions, never()).findByOwnerIdAndRevokedAtIsNullOrderByLastSeenAtDesc(main.getId());
    }

    @Test
    void oneShop_orPausedShops_nobodyElse() {
        Owner single = shop(null, "abhedyam_plan:yearly");
        when(owners.findByParentOwnerIdOrderByCreatedAtAsc(single.getId())).thenReturn(List.of());
        assertTrue(fcm.otherShopPhones(single).isEmpty());
        verifyNoInteractions(sessions);

        Owner main = shop(null, "abhedyam_plan:yearly"); // Business ended: the branch is paused
        Owner branch = shop(main.getId(), null);
        when(owners.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())).thenReturn(List.of(branch));
        assertTrue(fcm.otherShopPhones(main).isEmpty());
    }
}
