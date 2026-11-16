package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Business plan: add a shop, switch between shops. */
class ShopServiceTest {

    private final OwnerRepository owners = mock(OwnerRepository.class, org.mockito.Answers.CALLS_REAL_METHODS);
    private final AuthService auth = mock(AuthService.class);
    private final OwnerSettingsRepository settings = mock(OwnerSettingsRepository.class);
    private final com.abhedyam.service.interfaces.IOwnerService ownerService = mock(com.abhedyam.service.interfaces.IOwnerService.class);
    private final ShopService shops = new ShopService(owners, auth, settings, ownerService);

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
                () -> shops.add(main.getId(), null, "Second shop", null, null, null)).getErrorCode());
        verify(owners, never()).save(any());
    }

    @Test
    void add_OnBusiness_LinksToMainShop_EvenFromAnExtraShop_WithMainPhoneAndSettings() {
        Owner main = shop(null, "abhedyam_business:yearly");
        main.setPhone("6281666666");
        main.setPhoneNormalized("+916281666666");
        Owner extra = shop(main.getId(), null);
        when(owners.save(any(Owner.class))).thenAnswer(i -> { Owner o = i.getArgument(0); o.setId(UUID.randomUUID()); return o; });
        OwnerSettings ms = new OwnerSettings();
        ms.setOwnerId(main.getId());
        ms.setVillageModeEnabled(true);
        ms.setOtherFlags(Map.of("business", Map.of("villages", true)));
        when(settings.findByOwnerId(main.getId())).thenReturn(Optional.of(ms));

        shops.add(extra.getId(), null, " Village shop ", null, null, null);

        // Same bill phone as the main shop, but not its sign-in number (unique per account); no setup again.
        verify(auth).shopAuth(argThat(o -> main.getId().equals(o.getParentOwnerId()) && "Village shop".equals(o.getBusinessName())
                        && "6281666666".equals(o.getPhone()) && o.getPhoneNormalized() == null),
                eq(extra.getId()), isNull(), isNull(), isNull(), eq(false));
        verify(settings).save(argThat(s -> !main.getId().equals(s.getOwnerId()) && Boolean.TRUE.equals(s.getVillageModeEnabled())
                && s.getOtherFlags().containsKey("business")));
    }

    @Test
    void add_OwnPhone_SavedAsTenDigits_BadPhoneRefused() {
        Owner main = shop(null, "abhedyam_business:yearly");
        when(owners.save(any(Owner.class))).thenAnswer(i -> { Owner o = i.getArgument(0); o.setId(UUID.randomUUID()); return o; });

        shops.add(main.getId(), null, "Branch", "+91 94949 94996", null, null);
        verify(auth).shopAuth(argThat(o -> "9494994996".equals(o.getPhone())), any(), any(), any(), any(), anyBoolean());

        assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
                () -> shops.add(main.getId(), null, "Branch", "12345", null, null)).getErrorCode());
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

    @Test
    void list_ShowsEachShopsToCollectAndColour() {
        Owner main = shop(null, "abhedyam_business:yearly");
        Owner extra = shop(main.getId(), null);
        extra.setShopColour(3);
        when(owners.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())).thenReturn(List.of(extra));
        when(ownerService.toCollect(main.getId())).thenReturn(new java.math.BigDecimal("594100"));
        when(ownerService.toCollect(extra.getId())).thenReturn(java.math.BigDecimal.ZERO);

        var list = shops.list(main.getId());
        assertEquals(new java.math.BigDecimal("594100"), list.get(0).toCollect());
        assertNull(list.get(0).colour());
        assertEquals(java.math.BigDecimal.ZERO, list.get(1).toCollect());
        assertEquals(3, list.get(1).colour());
    }

    @Test
    void update_RenamesAndRecolours_OwnShopsOnly() {
        Owner main = shop(null, "abhedyam_business:yearly");
        Owner extra = shop(main.getId(), null);
        Owner stranger = shop(null, null);
        when(owners.save(any(Owner.class))).thenAnswer(i -> i.getArgument(0));

        var r = shops.update(extra.getId(), main.getId(), " Marava Main ", 5); // from an extra shop, the main one
        assertEquals("Marava Main", r.name());
        assertEquals(5, r.colour());
        assertTrue(r.main());
        shops.update(main.getId(), extra.getId(), null, 2); // colour only: the name stays
        assertEquals(2, extra.getShopColour());
        assertEquals("Shop " + extra.getId(), extra.getBusinessName());

        assertThrows(ResourceNotFoundException.class, () -> shops.update(main.getId(), stranger.getId(), "Mine now", null));
        assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
                () -> shops.update(main.getId(), extra.getId(), "  ", null)).getErrorCode());
        assertEquals("VALIDATION_ERROR", assertThrows(BusinessException.class,
                () -> shops.update(main.getId(), extra.getId(), null, 8)).getErrorCode());
    }
}
