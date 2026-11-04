package com.abhedyam.service;

import com.abhedyam.dto.ReminderCreateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Reminder;
import com.abhedyam.model.enums.ReminderChannel;
import com.abhedyam.model.enums.ReminderStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.ReminderRepository;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.IReminderService;
import com.abhedyam.util.PackageConstants;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReminderService implements IReminderService {
    
    private final ReminderRepository reminderRepository;
    private final CustomerRepository customerRepository;
    private final IAuditService auditService;
    
    @Override
    @Transactional
    public Reminder create(ReminderCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        if (request.getCustomerId() != null && !customerRepository.existsById(request.getCustomerId())) {
            throw new ResourceNotFoundException("Customer not found");
        }
        
        if (request.getDueAt().isBefore(Instant.now())) {
            throw new BusinessException("INVALID_DUE_DATE", "Due date cannot be in the past");
        }
        
        Reminder reminder = new Reminder();
        reminder.setCustomerId(request.getCustomerId());
        reminder.setOwnerId(ownerId);
        reminder.setName(request.getName());
        reminder.setType(request.getType());
        reminder.setChannel(ReminderChannel.IN_APP);
        reminder.setTime(request.getDueAt());
        reminder.setText(request.getText());
        reminder.setStatus(ReminderStatus.PENDING);
        
        List<String> packages = request.getPackages();
        if (packages == null || packages.isEmpty()) {
            packages = determinePackages(request.getCustomerId(), ownerId);
        }
        reminder.setPackages(packages);
        
        Reminder savedReminder = reminderRepository.save(reminder);
        
        String customerName = null;
        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            customerName = customer.getName();
        }
        
        auditService.logReminderCreation(savedReminder.getId(), ownerId, request.getCustomerId(), 
            customerName, request.getName(), request.getText(), request.getDueAt());
        
        savedReminder.getPackages().size();
        
        return savedReminder;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Reminder> getByCustomerId(UUID customerId) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<Reminder> reminders = reminderRepository.findByCustomerId(customerId);
        List<Reminder> filtered = reminders.stream()
            .filter(reminder -> reminder.getOwnerId().equals(ownerId))
            .sorted((r1, r2) -> {
                if (r1.getCreatedAt() == null && r2.getCreatedAt() == null) return 0;
                if (r1.getCreatedAt() == null) return 1;
                if (r2.getCreatedAt() == null) return -1;
                return r2.getCreatedAt().compareTo(r1.getCreatedAt());
            })
            .toList();
        filtered.forEach(r -> r.getPackages().size());
        return filtered;
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Reminder> getPendingReminders() {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        List<Reminder> reminders = reminderRepository.findByOwnerId(ownerId).stream()
            .filter(r -> r.getStatus() == ReminderStatus.PENDING)
            .filter(r -> r.getTime().isBefore(Instant.now()) || r.getTime().equals(Instant.now()))
            .toList();
        reminders.forEach(r -> r.getPackages().size());
        return reminders;
    }
    
    @Override
    @Transactional
    public Reminder markAsSent(UUID id) {
        Reminder reminder = reminderRepository.findById(id)
                .orElseThrow(() -> new com.abhedyam.exception.ResourceNotFoundException("Reminder not found"));
        reminder.setStatus(ReminderStatus.SENT);
        Reminder savedReminder = reminderRepository.save(reminder);
        savedReminder.getPackages().size();
        return savedReminder;
    }
    
    @Override
    @Transactional
    public void cancel(UUID ownerId, UUID id) {
        Reminder reminder = reminderRepository.findById(id)
                .filter(r -> ownerId.equals(r.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found"));
        reminder.setStatus(ReminderStatus.DELETED);
        reminder.setIsActive(false);
        reminderRepository.save(reminder);
    }

    private List<String> determinePackages(UUID customerId, UUID ownerId) {
        if (customerId != null) {
            return List.of(PackageConstants.CUSTOMER_APP_PACKAGE);
        } else if (ownerId != null) {
            return List.of(PackageConstants.BUSINESS_APP_PACKAGE);
        } else {
            return PackageConstants.ALL_PACKAGES;
        }
    }
}

