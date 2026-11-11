package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.FollowUpRequest;
import com.abhedyam.dto.FollowUpResponse;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.NotificationSettings;
import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.model.enums.ReminderType;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.util.SecurityUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** "Call again on…": 10 am push on the day, overdue once the day passed, done leaves the list, only own customers. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpsTest {
    @Mock ReminderRepository reminders;
    @Mock CustomerRepository customerRepository;
    @Mock LocationDetailsRepository locations;
    @Mock INotificationSettingsService notificationSettings;
    @InjectMocks PaymentDueService service;

    final UUID owner = UUID.randomUUID();
    final ZoneId ist = ZoneId.of("Asia/Kolkata");
    final List<Reminder> saved = new ArrayList<>();
    Customer bhaskar;
    MockedStatic<SecurityUtil> sec;

    @BeforeEach
    void setUp() {
        sec = mockStatic(SecurityUtil.class);
        sec.when(SecurityUtil::getCurrentUserId).thenReturn(owner);
        when(notificationSettings.forOwner(owner)).thenReturn(new NotificationSettings()); // Asia/Kolkata
        bhaskar = new Customer();
        bhaskar.setId(UUID.randomUUID());
        bhaskar.setOwnerId(owner);
        bhaskar.setName("Bhaskar Naidu");
        when(customerRepository.findById(bhaskar.getId())).thenReturn(Optional.of(bhaskar));
        when(customerRepository.findAllById(any())).thenReturn(List.of(bhaskar));
        when(locations.findByUserIdIn(any())).thenReturn(List.of());
        when(reminders.save(any())).thenAnswer(i -> {
            Reminder r = i.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            if (!saved.contains(r)) saved.add(r);
            return r;
        });
        when(reminders.findByOwnerId(owner)).thenReturn(saved);
        when(reminders.findById(any())).thenAnswer(i -> saved.stream().filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
    }

    @AfterEach
    void tearDown() { sec.close(); }

    FollowUpRequest on(LocalDate date) {
        FollowUpRequest r = new FollowUpRequest();
        r.setDate(date);
        return r;
    }

    @Test
    void tomorrow_is_pushed_at_10am_and_listed() {
        LocalDate tomorrow = LocalDate.now(ist).plusDays(1);
        FollowUpResponse f = service.createFollowUp(owner, bhaskar.getId(), on(tomorrow));
        Reminder r = saved.get(0);
        assertEquals(ReminderType.FOLLOW_UP, r.getType());
        assertEquals(ReminderStatus.PENDING, r.getStatus());
        assertEquals(tomorrow.atTime(10, 0).atZone(ist).toInstant(), r.getTime());
        assertEquals("Bhaskar Naidu", f.getCustomerName());
        assertEquals("UPCOMING", f.getStatus());
    }

    @Test
    void a_past_day_shows_overdue_and_done_removes_it() {
        LocalDate today = LocalDate.now(ist);
        FollowUpResponse f = service.createFollowUp(owner, bhaskar.getId(), on(today));
        saved.get(0).setTime(today.minusDays(2).atTime(10, 0).atZone(ist).toInstant()); // two days later, not called yet
        assertEquals("OVERDUE", service.ownerFollowUps(owner).get(0).getStatus());

        FollowUpRequest done = new FollowUpRequest();
        done.setDone(true);
        assertNull(service.updateFollowUp(owner, f.getId(), done));
        assertTrue(service.ownerFollowUps(owner).isEmpty());
    }

    @Test
    void moving_to_a_later_day_pushes_again() {
        LocalDate today = LocalDate.now(ist);
        FollowUpResponse f = service.createFollowUp(owner, bhaskar.getId(), on(today));
        saved.get(0).setStatus(ReminderStatus.SENT);
        service.updateFollowUp(owner, f.getId(), on(today.plusDays(3)));
        assertEquals(ReminderStatus.PENDING, saved.get(0).getStatus());
        assertTrue(saved.get(0).getTime().isAfter(Instant.now()));
    }

    @Test
    void another_shops_customer_is_refused() {
        bhaskar.setOwnerId(UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class,
                () -> service.createFollowUp(owner, bhaskar.getId(), on(LocalDate.now(ist).plusDays(1))));
        assertTrue(saved.isEmpty());
    }
}
