package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.repository.ReminderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderCancelTest {
    @Mock ReminderRepository repo;
    @InjectMocks ReminderService service;

    @Test
    void ownerCancelsItsReminder() {
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        Reminder r = new Reminder();
        r.setOwnerId(owner);
        r.setStatus(ReminderStatus.PENDING);
        when(repo.findById(id)).thenReturn(Optional.of(r));
        service.cancel(owner, id);
        assertEquals(ReminderStatus.DELETED, r.getStatus());
        verify(repo).save(r);
    }

    @Test
    void anotherOwnerCannotCancel() {
        UUID id = UUID.randomUUID();
        Reminder r = new Reminder();
        r.setOwnerId(UUID.randomUUID());
        when(repo.findById(id)).thenReturn(Optional.of(r));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(UUID.randomUUID(), id));
        verify(repo, never()).save(any());
    }
}
