package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.repository.OwnerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Business plan: add a shop, switch between shops. */
class ShopServiceTest {

    private final OwnerRepository owners = mock(OwnerRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    private final AuthService auth = mock(AuthService.class);
    private final ShopService shops = new ShopService(owners, auth);

    private Owner shop(UUID parent, String product) {
        Owner o = new Owner();
        o.setId(UUID.randomUUID());
        o.setBusinessName("Shop " + o.getId());
        o.setIsActive(true);
        o.setParentOwnerId(parent);
        o.setAccessUntil(parent == null ? Instant.now().plusSeconds(86400) : null);
        o.setPlayProductId(product);
        when(owners.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void add_NeedsBusiness() {
        Owner main = shop(null, "abhedyam_plan:yearly");
        assertEquals("PLAN_LIMIT", assertThrows(BusinessException.class,
                () -> shops.add(main.getId(), null, "Second shop", null, null)).getErrorCode());
        verify(owners, never()).save(any());
    }

    @Test
    void add_OnBusiness_LinksToMainShop_EvenFromAnExtraShop() {
        Owner main = shop(null, "abhedyam_business:yearly");
        Owner extra = shop(main.getId(), null);
        when(owners.save(any(Owner.class))).thenAnswer(i -> { Owner o = i.getArgument(0); o.setId(UUID.randomUUID()); return o; });

        shops.add(extra.getId(), null, " Village shop ", null, null);

        verify(auth).shopAuth(argThat(o -> main.getId().equals(o.getParentOwnerId()) && "Village shop".equals(o.getBusinessName())),
                eq(extra.getId()), isNull(), isNull(), isNull(), eq(true));
    }

    @Test
    void switch_OnlyToOwnShops_AndListShowsPaused() {
        Owner main = shop(null, "abhedyam_plan:yearly"); // Business ended
        Owner extra = shop(main.getId(), null);
        Owner stranger = shop(null, null);
        when(owners.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())).thenReturn(List.of(extra));

        assertThrows(ResourceNotFoundException.class, () -> shops.switchTo(main.getId(), null, stranger.getId(), null, null));
        shops.switchTo(main.getId(), null, extra.getId(), null, null); // AuthService refuses it with PLAN_PAUSED
        verify(auth).shopAuth(eq(extra), eq(main.getId()), any(), any(), any(), eq(false));

        var list = shops.list(extra.getId());
        assertEquals(2, list.size());
        assertTrue(list.get(0).main());
        assertTrue(list.get(1).current());
        assertTrue(list.get(1).paused());
    }
}
