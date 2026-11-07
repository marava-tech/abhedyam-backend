package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.OwnerDueResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.dto.UpcomingDuesResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Payment;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.PaymentDueRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.service.interfaces.ICustomerService;
import com.abhedyam.service.interfaces.INotificationSettingsService;
import com.abhedyam.service.interfaces.IPaymentDueService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentDueService implements IPaymentDueService {

    private static final Comparator<PaymentDue> BY_DATE =
            Comparator.comparing(PaymentDue::getDueDate).thenComparing(PaymentDue::getCreatedAt);

    private final PaymentDueRepository dueRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    /** Its payments summary checks that the owner is the caller and owns the customer, and gives the balance. */
    private final ICustomerService customerService;
    private final INotificationSettingsService notificationSettings;

    /** Dues of one customer with what is paid against each, plus the balance they were checked against. */
    private record State(BigDecimal balance, LocalDate today, List<PaymentDue> dues, Map<UUID, BigDecimal> paid) {
        BigDecimal paidOf(PaymentDue d) { return paid.getOrDefault(d.getId(), BigDecimal.ZERO); }

        BigDecimal remainingOf(PaymentDue d) { return d.getAmount().subtract(paidOf(d)).max(BigDecimal.ZERO); }

        PaymentDueResponse response(PaymentDue d) { return PaymentDueService.response(d, paidOf(d), today); }

        /** Unpaid part of every due except the one being edited (null = none). */
        BigDecimal plannedExcept(UUID skip) {
            return dues.stream().filter(d -> !d.getId().equals(skip)).map(this::remainingOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    static PaymentDueResponse response(PaymentDue d, BigDecimal paid, LocalDate today) {
        return new PaymentDueResponse(d.getId(), d.getAmount(), paid, d.getAmount().subtract(paid).max(BigDecimal.ZERO),
                d.getDueDate(), d.getNote(), DueAllocator.status(d.getAmount(), paid, d.getDueDate(), today).name(),
                d.getCreatedAt(), d.getEarlyReminderDays());
    }

    /** Paid per due for dues of one customer and that customer's payments (any status; only SUCCESS counts). */
    private static Map<UUID, BigDecimal> allocate(List<PaymentDue> dues, List<Payment> payments) {
        return DueAllocator.allocate(
                dues.stream().map(d -> new DueAllocator.Due(d.getId(), d.getAmount(), d.getDueDate(), d.getCreatedAt())).toList(),
                payments.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                        .map(p -> new DueAllocator.Paid(p.getAmount(), p.getCreatedAt())).toList());
    }

    private LocalDate today(UUID ownerId) {
        return LocalDate.now(ZoneId.of(notificationSettings.forOwner(ownerId).getTimezone()));
    }

    private State load(UUID ownerId, UUID customerId) {
        BigDecimal balance = customerService.getCustomerPaymentsSummary(ownerId, customerId).getDueAmount();
        List<PaymentDue> dues = new ArrayList<>(dueRepository.findByOwnerIdAndCustomerIdAndIsActiveTrue(ownerId, customerId));
        dues.sort(BY_DATE);
        Map<UUID, BigDecimal> paid = allocate(dues, paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId));
        return new State(balance, today(ownerId), dues, paid);
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
    @Transactional(readOnly = true)
    public List<OwnerDueResponse> ownerDues(UUID ownerId) {
        List<PaymentDue> all = dueRepository.findByOwnerIdAndIsActiveTrue(ownerId);
        if (all.isEmpty()) return List.of();
        Map<UUID, List<PaymentDue>> byCustomer = all.stream().collect(Collectors.groupingBy(PaymentDue::getCustomerId));
        List<UUID> ids = new ArrayList<>(byCustomer.keySet());
        Map<UUID, List<Payment>> payments = paymentRepository.findByCustomerIdInAndOwnerId(ids, ownerId).stream()
                .collect(Collectors.groupingBy(Payment::getCustomerId));
        Map<UUID, Customer> customers = customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        LocalDate today = today(ownerId);
        List<OwnerDueResponse> out = new ArrayList<>();
        byCustomer.forEach((customerId, dues) -> {
            Map<UUID, BigDecimal> paid = allocate(dues, payments.getOrDefault(customerId, List.of()));
            Customer c = customers.get(customerId);
            for (PaymentDue d : dues) {
                out.add(new OwnerDueResponse(customerId, c == null ? null : c.getName(), c == null ? null : c.getPhone(),
                        response(d, paid.get(d.getId()), today)));
            }
        });
        out.sort(Comparator.comparing((OwnerDueResponse o) -> o.getDue().getDueDate()).thenComparing(o -> o.getDue().getCreatedAt()));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public UpcomingDuesResponse upcoming(UUID ownerId, int days) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own dues");
        }
        LocalDate end = today(ownerId).plusDays(Math.max(0, Math.min(days, 90)));
        List<OwnerDueResponse> missed = new ArrayList<>(), upcoming = new ArrayList<>();
        for (OwnerDueResponse o : ownerDues(ownerId)) {
            String st = o.getDue().getStatus();
            if (st.equals("MISSED")) missed.add(o);
            else if (!st.equals("PAID") && !o.getDue().getDueDate().isAfter(end)) upcoming.add(o);
        }
        List<OwnerDueResponse> dues = new ArrayList<>(missed);
        dues.addAll(upcoming);
        return new UpcomingDuesResponse(sum(upcoming), missed.size(), sum(missed), dues);
    }

    static BigDecimal sum(List<OwnerDueResponse> dues) {
        return dues.stream().map(o -> o.getDue().getRemaining()).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        d.setEarlyReminderDays(r.getEarlyReminderDays() == null ? null : remindDays(r.getEarlyReminderDays()));
        d = dueRepository.save(d);
        // Recorded now, so no earlier payment counts toward it.
        return response(d, BigDecimal.ZERO, s.today());
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
        if (r.getEarlyReminderDays() != null) d.setEarlyReminderDays(remindDays(r.getEarlyReminderDays()));
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

    /** 0 = no extra reminder (null); otherwise 2..30 (1 is the reminder every due already gets). */
    static Integer remindDays(int days) {
        if (days == 0 || days == 1) return null;
        if (days < 0 || days > 30) throw invalid("An earlier reminder can be 2 to 30 days before");
        return days;
    }

    private static String note(String note) {
        if (note == null || note.isBlank()) return null;
        String t = note.trim();
        if (t.length() > 200) throw invalid("Note can be at most 200 characters");
        return t;
    }

    static String rupees(BigDecimal v) {
        return DueDigest.indian(v.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCodes.VALIDATION_ERROR, message);
    }
}
