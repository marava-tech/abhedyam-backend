package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.SaleCreateRequest;
import com.abhedyam.dto.SaleDetailResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.SaleItem;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.security.UserPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** A sale saved offline and sent again (hours later, Redis forgot it) returns the first sale; nothing new is made. */
@ExtendWith(MockitoExtension.class)
class SaleIdempotencyTest {

    @Mock SaleItemRepository saleItemRepository;
    @Mock CustomerRepository customerRepository;
    @Spy @InjectMocks SaleService saleService;

    UUID owner = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(owner, null), null, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keySeenBefore_ReturnsFirstSale() {
        SaleItem first = new SaleItem();
        first.setTransactionId("tx-1");
        when(saleItemRepository.findFirstByOwnerIdAndClientKey(owner, "k1")).thenReturn(Optional.of(first));
        SaleDetailResponse earlier = new SaleDetailResponse();
        doReturn(earlier).when(saleService).getSaleByTransactionId("tx-1");

        SaleCreateRequest req = new SaleCreateRequest();
        req.setIdempotencyKey(" k1 ");
        req.setSoldAt(Instant.now().minus(3, ChronoUnit.HOURS));

        assertSame(earlier, saleService.createSale(req));
        verify(saleItemRepository, never()).save(any());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void soldAtInTheFuture_IsRefusedBeforeAnythingIsMade() {
        SaleCreateRequest req = new SaleCreateRequest();
        req.setIdempotencyKey("k2");
        req.setSoldAt(Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(BusinessException.class, () -> saleService.createSale(req));
        verifyNoInteractions(saleItemRepository, customerRepository);
    }
}
