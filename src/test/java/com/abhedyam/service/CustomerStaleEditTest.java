package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.CustomerUpdateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.security.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Two phones edit the same customer: the one that loaded an older copy gets 409 instead of overwriting. */
@ExtendWith(MockitoExtension.class)
class CustomerStaleEditTest {

    @Mock private CustomerRepository customerRepository;
    @InjectMocks private CustomerService customerService;

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void olderEditVersion_IsRejectedAndNothingSaved() {
        UUID ownerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(ownerId, null), null, List.of()));
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setOwnerId(ownerId);
        c.setEditVersion(4); // phone A already saved
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));

        CustomerUpdateRequest req = new CustomerUpdateRequest();
        req.setName("Prava");
        req.setEditVersion(3L); // phone B loaded before that save

        BusinessException e = assertThrows(BusinessException.class, () -> customerService.update(c.getId(), req));
        assertEquals("STALE_EDIT", e.getErrorCode());
        assertEquals(4, c.getEditVersion());
        verify(customerRepository, never()).save(any());
    }
}
