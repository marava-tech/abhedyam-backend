package com.abhedyam.service;

import com.abhedyam.dto.ApplyReferralRequest;
import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Referral;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.enums.PaymentMedium;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ReferralRepository;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.service.interfaces.ISubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock
    private OwnerRepository ownerRepository;

    @Mock
    private ReferralRepository referralRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private ISubscriptionService subscriptionService;

    @InjectMocks
    private ReferralService referralService;

    private UUID referrerId;
    private UUID refereeId;
    private Owner referrer;
    private Owner referee;

    @BeforeEach
    void setUp() {
        referrerId = UUID.randomUUID();
        refereeId = UUID.randomUUID();

        referrer = new Owner();
        referrer.setId(referrerId);
        referrer.setName("Referrer Merchant");
        referrer.setBusinessName("Referrer Shop");
        referrer.setReferralCode("ABH12345");
        referrer.setDeviceHardwareId("DEVICE_REF_1");

        referee = new Owner();
        referee.setId(refereeId);
        referee.setName("Referee Merchant");
        referee.setBusinessName("Referee Shop");
        referee.setDeviceHardwareId("DEVICE_NEW_2");
    }

    @Test
    void testGetOrCreateReferralCode_WhenNull_GeneratesNew() {
        Owner owner = new Owner();
        owner.setId(UUID.randomUUID());
        owner.setReferralCode(null);

        when(ownerRepository.existsByReferralCode(anyString())).thenReturn(false);
        when(ownerRepository.save(any(Owner.class))).thenAnswer(inv -> inv.getArgument(0));

        String code = referralService.getOrCreateReferralCode(owner);

        assertNotNull(code);
        assertTrue(code.startsWith("ABH"));
        assertEquals(8, code.length());
        verify(ownerRepository).save(owner);
    }

    @Test
    void testApplyReferralCode_SelfReferral_ThrowsException() {
        when(ownerRepository.findById(refereeId)).thenReturn(Optional.of(referee));
        when(referralRepository.existsByRefereeOwnerId(refereeId)).thenReturn(false);
        when(ownerRepository.findByReferralCode("ABH12345")).thenReturn(Optional.of(referee)); // same owner

        ApplyReferralRequest req = new ApplyReferralRequest("ABH12345", "DEVICE_NEW_2");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                referralService.applyReferralCode(refereeId, req));
        assertEquals("SELF_REFERRAL", ex.getErrorCode());
    }

    @Test
    void testApplyReferralCode_SameDevice_ThrowsException() {
        referee.setDeviceHardwareId("DEVICE_REF_1"); // same as referrer
        when(ownerRepository.findById(refereeId)).thenReturn(Optional.of(referee));
        when(referralRepository.existsByRefereeOwnerId(refereeId)).thenReturn(false);
        when(ownerRepository.findByReferralCode("ABH12345")).thenReturn(Optional.of(referrer));

        ApplyReferralRequest req = new ApplyReferralRequest("ABH12345", "DEVICE_REF_1");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                referralService.applyReferralCode(refereeId, req));
        assertEquals("DEVICE_CONFLICT", ex.getErrorCode());
    }

    @Test
    void testApplyReferralCode_Success_GrantsWelcomeBonus() {
        when(ownerRepository.findById(refereeId)).thenReturn(Optional.of(referee));
        when(referralRepository.existsByRefereeOwnerId(refereeId)).thenReturn(false);
        when(ownerRepository.findByReferralCode("ABH12345")).thenReturn(Optional.of(referrer));
        when(referralRepository.save(any(Referral.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplyReferralRequest req = new ApplyReferralRequest("ABH12345", "DEVICE_NEW_2");
        referralService.applyReferralCode(refereeId, req);

        verify(referralRepository).save(any(Referral.class));
        verify(subscriptionService).grantPromotionalDays(refereeId, 30);
    }

    @Test
    void testEvaluateReferral_WhenThresholdsMet_RewardsReferrer() {
        Referral ref = new Referral();
        ref.setId(UUID.randomUUID());
        ref.setReferrerOwnerId(referrerId);
        ref.setRefereeOwnerId(refereeId);
        ref.setStatus(ReferralStatus.PENDING);
        ref.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));

        when(customerRepository.countByOwnerId(refereeId)).thenReturn(28L); // >= 25

        Instant day1 = Instant.now().minus(4, ChronoUnit.DAYS);
        Instant day2 = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant day3 = Instant.now();

        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Payment p = new Payment();
            p.setTimestamp(i % 2 == 0 ? day1 : day2);
            p.setAmount(new BigDecimal("300.00"));
            p.setWriteOff(false);
            payments.add(p);
        }

        List<SaleItem> sales = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            SaleItem s = new SaleItem();
            s.setCreatedAt(day3);
            s.setPrice(new BigDecimal("200.00"));
            s.setQuantity(BigDecimal.ONE);
            sales.add(s);
        }

        when(paymentRepository.findByOwnerId(refereeId)).thenReturn(payments);
        when(saleItemRepository.findByOwnerId(refereeId)).thenReturn(sales);
        when(reminderRepository.findByOwnerId(refereeId)).thenReturn(Collections.emptyList());

        referralService.evaluateReferral(ref);

        assertEquals(ReferralStatus.REWARDED, ref.getStatus());
        assertNotNull(ref.getRewardedAt());
        verify(subscriptionService).grantPromotionalDays(referrerId, 30);
        verify(referralRepository).save(ref);
    }
}
