package com.abhedyam.service.interfaces;

import com.abhedyam.dto.ReminderCreateRequest;
import com.abhedyam.model.Reminder;

import java.util.List;
import java.util.UUID;

public interface IReminderService {
    Reminder create(ReminderCreateRequest request);
    List<Reminder> getByCustomerId(UUID customerId);
    /** Cancels a not-yet-sent reminder so it is never delivered. Only its owner may cancel it. */
    void cancel(UUID ownerId, UUID id);
}

