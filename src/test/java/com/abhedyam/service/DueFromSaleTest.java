package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.repository.PaymentDueRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A New sale with a due date adds a due for what is left unpaid, on the owner's day. */
@ExtendWith(MockitoExtension.class)
class DueFromSaleTest {
    @Mock PaymentDueRepository dueRepository;
    @Mock INotificationSettingsService notificationSettings;
    @InjectMocks PaymentDueService service;

    final UUID owner = UUID.randomUUID(), customer = UUID.randomUUID();
    final ZoneId ist = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        lenient().when(notificationSettings.forOwner(owner)).thenReturn(new NotificationSettings()); // Asia/Kolkata
    }

    @Test
    void unpaidPartBecomesADueOnTheOwnersDay() {
        LocalDate in7 = LocalDate.now(ist).plusDays(7);
        // the app sends local midnight as UTC: 18:30 the day before
        service.addFromSale(owner, customer, new BigDecimal("3600"), in7.atStartOfDay(ist).toInstant());
        ArgumentCaptor<PaymentDue> due = ArgumentCaptor.forClass(PaymentDue.class);
        verify(dueRepository).save(due.capture());
        assertEquals(in7, due.getValue().getDueDate());
        assertEquals(new BigDecimal("3600"), due.getValue().getAmount());
        assertEquals(customer, due.getValue().getCustomerId());
        assertEquals(owner, due.getValue().getOwnerId());
    }

    @Test
    void nothingWhenFullyPaidNoDateOrDateAlreadyPast() {
        var in7 = LocalDate.now(ist).plusDays(7).atStartOfDay(ist).toInstant();
        service.addFromSale(owner, customer, BigDecimal.ZERO, in7);
        service.addFromSale(owner, customer, new BigDecimal("100"), null);
        service.addFromSale(owner, customer, new BigDecimal("100"), LocalDate.now(ist).minusDays(1).atStartOfDay(ist).toInstant());
        verify(dueRepository, never()).save(any());
    }
}
