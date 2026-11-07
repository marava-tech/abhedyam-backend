package com.abhedyam.service;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.dto.LocationDetailsUpdateRequest;
import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A village is a label and a map pin is separate: either can exist without the other, and no 0,0 pins are stored. */
@ExtendWith(MockitoExtension.class)
class VillagePinSplitTest {

    @Mock LocationDetailsRepository locations;
    @Mock CustomerRepository customers;
    @InjectMocks LocationDetailsService service;

    final UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(new UserPrincipal(owner, null), null, java.util.List.of()));
        Customer c = new Customer();
        c.setId(customer);
        c.setOwnerId(owner);
        when(customers.findById(customer)).thenReturn(Optional.of(c));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aVillageAloneIsSavedWithoutAPinAndSentAsZero() {
        when(locations.findByUserId(customer)).thenReturn(Optional.empty());
        when(locations.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new LocationDetailsUpdateRequest();
        req.setVillage("Warangal");

        var res = service.updateCustomerLocation(customer, req);

        verify(locations).save(argThat((LocationDetails l) -> l.getLatitude() == null && l.getLongitude() == null && "Warangal".equals(l.getVillage())));
        assertEquals(0, res.getLatitude().compareTo(BigDecimal.ZERO)); // released apps parse latitude as a required number
    }

    @Test
    void neitherPinNorVillageIsRefused() {
        when(locations.findByUserId(customer)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.updateCustomerLocation(customer, new LocationDetailsUpdateRequest()));
    }

    @Test
    void savingAPinLeavesTheVillageAlone() {
        LocationDetails existing = new LocationDetails();
        existing.setUserId(customer);
        existing.setVillage("Warangal");
        when(locations.findByUserId(customer)).thenReturn(Optional.of(existing));
        when(locations.save(any())).thenAnswer(i -> i.getArgument(0));
        var req = new LocationDetailsUpdateRequest();
        req.setLatitude(new BigDecimal("17.9689"));
        req.setLongitude(new BigDecimal("79.5941"));

        service.updateCustomerLocation(customer, req);

        assertEquals("Warangal", existing.getVillage());
        assertEquals(0, existing.getLatitude().compareTo(new BigDecimal("17.9689")));
    }
}
