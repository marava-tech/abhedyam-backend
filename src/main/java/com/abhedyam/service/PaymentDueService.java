package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.PaymentDueRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.service.interfaces.IPaymentDueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentDueService implements IPaymentDueService {

    private final PaymentDueRepository dueRepository;
    private final PaymentRepository paymentRepository;
    /** Its payments summary checks that the owner is the caller and owns the customer, and gives the balance. */
    private final ICustomerService customerService;
    private final INotificationSettingsService notificationSettings;

    /** Dues of one customer with what is paid against each, plus the balance they were checked against. */
    private record State(BigDecimal balance, LocalDate today, List<PaymentDue> dues, Map<UUID, BigDecimal> paid) {
        BigDecimal paidOf(PaymentDue d) { return paid.getOrDefault(d.getId(), BigDecimal.ZERO); }

        BigDecimal remainingOf(PaymentDue d) { return d.getAmount().subtract(paidOf(d)).max(BigDecimal.ZERO); }

        PaymentDueResponse response(PaymentDue d) {
            BigDecimal p = paidOf(d);
            return new PaymentDueResponse(d.getId(), d.getAmount(), p, remainingOf(d), d.getDueDate(), d.getNote(),
                    DueAllocator.status(d.getAmount(), p, d.getDueDate(), today).name(), d.getCreatedAt());
        }

        /** Unpaid part of every due except the one being edited (null = none). */
        BigDecimal plannedExcept(UUID skip) {
            return dues.stream().filter(d -> !d.getId().equals(skip)).map(this::remainingOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private State load(UUID ownerId, UUID customerId) {
        BigDecimal balance = customerService.getCustomerPaymentsSummary(ownerId, customerId).getDueAmount();
        List<PaymentDue> dues = dueRepository.findByOwnerIdAndCustomerIdAndIsActiveTrue(ownerId, customerId);
        dues.sort(Comparator.comparing(PaymentDue::getDueDate).thenComparing(PaymentDue::getCreatedAt));
        List<DueAllocator.Paid> payments = paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(p -> new DueAllocator.Paid(p.getAmount(), p.getCreatedAt()))
                .toList();
        Map<UUID, BigDecimal> paid = DueAllocator.allocate(dues.stream()
                .map(d -> new DueAllocator.Due(d.getId(), d.getAmount(), d.getDueDate(), d.getCreatedAt())).toList(), payments);
        LocalDate today = LocalDate.now(ZoneId.of(notificationSettings.forOwner(ownerId).getTimezone()));
        return new State(balance, today, dues, paid);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDuesResponse list(UUID ownerId, UUID customerId) {
        State s = load(ownerId, customerId);
        List<PaymentDueResponse> dues = s.dues().stream().map(s::response).toList();
        BigDecimal planned = s.plannedExcept(null);
        long missed = dues.stream().filter(d -> d.getStatus().equals("MISSED")).count();
        PaymentDueResponse next = dues.stream().filter(d -> !d.getStatus().equals("PAID")).findFirst().orElse(null);
        return new CustomerDuesResponse(s.balance(), planned, s.balance().subtract(planned).max(BigDecimal.ZERO),
                missed, next, dues);
    }

    @Override
    @Transactional
    public PaymentDueResponse create(UUID ownerId, UUID customerId, PaymentDueRequest r) {
        if (r.getAmount() == null || r.getDueDate() == null) throw invalid("Enter an amount and a due date");
        State s = load(ownerId, customerId);
        BigDecimal amount = checkAmount(r.getAmount());
        checkDate(r.getDueDate(), s.today());
        checkFits(amount, s, null);
        PaymentDue d = new PaymentDue();
        d.setOwnerId(ownerId);
        d.setCustomerId(customerId);
        d.setAmount(amount);
        d.setDueDate(r.getDueDate());
        d.setNote(note(r.getNote()));
        d = dueRepository.save(d);
        // Recorded now, so no earlier payment counts toward it.
        return new PaymentDueResponse(d.getId(), amount, BigDecimal.ZERO, amount, d.getDueDate(), d.getNote(),
                DueAllocator.status(amount, BigDecimal.ZERO, d.getDueDate(), s.today()).name(), d.getCreatedAt());
    }

    @Override
    @Transactional
    public PaymentDueResponse update(UUID ownerId, UUID customerId, UUID dueId, PaymentDueRequest r) {
        State s = load(ownerId, customerId);
        PaymentDue d = find(s, dueId);
        if (r.getAmount() != null) {
            BigDecimal amount = checkAmount(r.getAmount());
            BigDecimal paid = s.paidOf(d);
            if (amount.compareTo(paid) < 0) {
                throw invalid("₹" + rupees(paid) + " is already paid against this due; the amount can't be lower");
            }
            checkFits(amount.subtract(paid), s, d.getId());
            d.setAmount(amount);
        }
        if (r.getDueDate() != null && !r.getDueDate().equals(d.getDueDate())) {
            checkDate(r.getDueDate(), s.today());
            d.setDueDate(r.getDueDate());
        }
        if (r.getNote() != null) d.setNote(note(r.getNote()));
        dueRepository.save(d);
        return load(ownerId, customerId).response(d);
    }

    @Override
    @Transactional
    public void delete(UUID ownerId, UUID customerId, UUID dueId) {
        PaymentDue d = find(load(ownerId, customerId), dueId);
        d.setIsActive(false);
        d.setDeletedAt(Instant.now());
        dueRepository.save(d);
    }

    private static PaymentDue find(State s, UUID dueId) {
        return s.dues().stream().filter(d -> d.getId().equals(dueId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Due could not be found"));
    }

    private static BigDecimal checkAmount(BigDecimal amount) {
        if (amount.signum() <= 0) throw invalid("Amount must be more than ₹0");
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static void checkDate(LocalDate date, LocalDate today) {
        if (date.isBefore(today)) throw invalid("The due date can't be in the past");
    }

    /** unpaid part of this due + unpaid part of the others must fit in the balance. */
    private static void checkFits(BigDecimal unpaid, State s, UUID skip) {
        BigDecimal left = s.balance().subtract(s.plannedExcept(skip));
        if (unpaid.compareTo(left) > 0) {
            throw invalid(left.signum() <= 0 ? "The whole balance already has due dates"
                    : "Only ₹" + rupees(left) + " is left to plan");
        }
    }

    private static String note(String note) {
        if (note == null || note.isBlank()) return null;
        String t = note.trim();
        if (t.length() > 200) throw invalid("Note can be at most 200 characters");
        return t;
    }

    private static String rupees(BigDecimal v) {
        return DueDigest.indian(v.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCodes.VALIDATION_ERROR, message);
    }
}
