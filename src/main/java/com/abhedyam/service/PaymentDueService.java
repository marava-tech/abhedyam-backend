package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CollectionVisitRequest;
import com.abhedyam.dto.CollectionVisitResponse;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.OwnerDueResponse;
import com.abhedyam.dto.PaymentDueRequest;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.dto.UpcomingDuesResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.CollectionVisit;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Payment;
import com.abhedyam.model.PaymentDue;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.CollectionVisitRepository;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.LocationDetailsRepository;
import com.abhedyam.model.LocationDetails;
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
    private final LocationDetailsRepository locations;
    private final CollectionVisitRepository visitRepository;
    private final SaleItemRepository saleItems;
    /** Its payments summary checks that the owner is the caller and owns the customer, and gives the balance. */
    private final ICustomerService customerService;
    private final INotificationSettingsService notificationSettings;
    private final com.abhedyam.service.interfaces.IOwnerSettingsService ownerSettings;

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
                        .map(p -> new DueAllocator.Paid(p.getAmount(), p.getCreatedAt(), p.getDueId())).toList());
    }

    private LocalDate today(UUID ownerId) {
        return LocalDate.now(ZoneId.of(notificationSettings.forOwner(ownerId).getTimezone()));
    }

    private State load(UUID ownerId, UUID customerId) {
        return load(ownerId, customerId, customerService.getCustomerPaymentsSummary(ownerId, customerId).getDueAmount());
    }

    private State load(UUID ownerId, UUID customerId, BigDecimal balance) {
        List<PaymentDue> dues = new ArrayList<>(dueRepository.findByOwnerIdAndCustomerIdAndIsActiveTrue(ownerId, customerId));
        dues.sort(BY_DATE);
        Map<UUID, BigDecimal> paid = allocate(dues, paymentRepository.findByCustomerIdAndOwnerId(customerId, ownerId));
        return new State(balance, today(ownerId), dues, paid);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDuesResponse list(UUID ownerId, UUID customerId) {
        return list(load(ownerId, customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDuesResponse listForLink(UUID ownerId, UUID customerId, BigDecimal balance) {
        return list(load(ownerId, customerId, balance));
    }

    private CustomerDuesResponse list(State s) {
        List<PaymentDueResponse> dues = s.dues().stream().map(s::response).toList();
        BigDecimal planned = s.plannedExcept(null);
        long overdue = dues.stream().filter(d -> d.getStatus().equals("OVERDUE")).count();
        PaymentDueResponse next = dues.stream().filter(d -> !d.getStatus().equals("PAID")).findFirst().orElse(null);
        return new CustomerDuesResponse(s.balance(), planned, s.balance().subtract(planned).max(BigDecimal.ZERO),
                overdue, next, dues);
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
        Map<UUID, String> villages = new HashMap<>();
        for (LocationDetails l : locations.findByUserIdIn(ids)) {
            if (l.getVillage() != null && !l.getVillage().isBlank()) villages.putIfAbsent(l.getUserId(), l.getVillage());
        }
        LocalDate today = today(ownerId);
        List<OwnerDueResponse> out = new ArrayList<>();
        byCustomer.forEach((customerId, dues) -> {
            Map<UUID, BigDecimal> paid = allocate(dues, payments.getOrDefault(customerId, List.of()));
            Customer c = customers.get(customerId);
            for (PaymentDue d : dues) {
                out.add(new OwnerDueResponse(customerId, c == null ? null : c.getName(), c == null ? null : c.getPhone(),
                        villages.get(customerId),
                        response(d, paid.get(d.getId()), today)));
            }
        });
        out.sort(Comparator.comparing((OwnerDueResponse o) -> o.getDue().getDueDate()).thenComparing(o -> o.getDue().getCreatedAt()));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public UpcomingDuesResponse upcoming(UUID ownerId, int days) {
        requireSelf(ownerId);
        LocalDate end = today(ownerId).plusDays(Math.max(0, Math.min(days, 365)));
        List<OwnerDueResponse> overdue = new ArrayList<>(), upcoming = new ArrayList<>();
        for (OwnerDueResponse o : ownerDues(ownerId)) {
            String st = o.getDue().getStatus();
            if (st.equals("OVERDUE")) overdue.add(o);
            else if (!st.equals("PAID") && !o.getDue().getDueDate().isAfter(end)) upcoming.add(o);
        }
        List<OwnerDueResponse> dues = new ArrayList<>(overdue);
        dues.addAll(upcoming);
        List<CollectionVisitResponse> visits = !ownerSettings.villageMode(ownerId) ? List.of() : ownerVisits(ownerId).stream()
                .filter(v -> v.getStatus().equals("OVERDUE") || !v.getVisitDate().isAfter(end))
                .sorted(Comparator.comparing((CollectionVisitResponse v) -> !v.getStatus().equals("OVERDUE"))
                        .thenComparing(CollectionVisitResponse::getVisitDate))
                .toList();
        return new UpcomingDuesResponse(sum(upcoming), overdue.size(), sum(overdue), dues, visits);
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

    @Override
    @Transactional(readOnly = true)
    public List<CollectionVisitResponse> ownerVisits(UUID ownerId) {
        List<CollectionVisit> visits = visitRepository.findByOwnerIdAndIsActiveTrueAndDoneAtIsNull(ownerId);
        if (visits.isEmpty()) return List.of();
        Map<UUID, String> villageByCustomer = new HashMap<>();
        for (Object[] row : locations.findCustomerVillagesByOwnerId(ownerId)) {
            villageByCustomer.put(UUID.fromString(row[0].toString()), (String) row[1]);
        }
        Map<String, VillagePendingCalculator.Owing> owing = VillagePendingCalculator.owingByVillage(
                amounts(saleItems.sumTotalsByCustomer(ownerId)), amounts(paymentRepository.sumSuccessfulByCustomer(ownerId)),
                villageByCustomer);
        LocalDate today = today(ownerId);
        return visits.stream().map(v -> visitResponse(v, owing.get(VillagePendingCalculator.key(v.getVillage())), today)).toList();
    }

    private static CollectionVisitResponse visitResponse(CollectionVisit v, VillagePendingCalculator.Owing o, LocalDate today) {
        return new CollectionVisitResponse(v.getId(), v.getVillage(), v.getVisitDate(), v.getNote(),
                v.getVisitDate().isBefore(today) ? "OVERDUE" : "UPCOMING",
                o == null ? 0 : o.customers(), o == null ? BigDecimal.ZERO : o.amount());
    }

    /** Same parsing as LocationDetailsService: row types vary by driver. */
    private static Map<UUID, BigDecimal> amounts(List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put(UUID.fromString(r[0].toString()), new BigDecimal(r[1].toString()));
        return m;
    }

    @Override
    @Transactional
    public CollectionVisitResponse createVisit(UUID ownerId, CollectionVisitRequest r) {
        requireSelf(ownerId);
        if (r.getVillage() == null || r.getVillage().isBlank() || r.getVisitDate() == null) {
            throw invalid("Pick a village and a date");
        }
        String village = r.getVillage().trim();
        if (village.length() > 120) throw invalid("Village name is too long");
        checkDate(r.getVisitDate(), today(ownerId));
        CollectionVisit v = new CollectionVisit();
        v.setOwnerId(ownerId);
        v.setVillage(village);
        v.setVisitDate(r.getVisitDate());
        v.setNote(note(r.getNote()));
        visitRepository.save(v);
        return find(ownerVisits(ownerId), v.getId());
    }

    @Override
    @Transactional
    public CollectionVisitResponse updateVisit(UUID ownerId, UUID visitId, CollectionVisitRequest r) {
        CollectionVisit v = ownVisit(ownerId, visitId);
        if (r.getVisitDate() != null && !r.getVisitDate().equals(v.getVisitDate())) {
            checkDate(r.getVisitDate(), today(ownerId));
            v.setVisitDate(r.getVisitDate());
        }
        if (r.getNote() != null) v.setNote(note(r.getNote()));
        if (Boolean.TRUE.equals(r.getDone())) v.setDoneAt(Instant.now());
        visitRepository.save(v);
        CollectionVisitResponse open = ownerVisits(ownerId).stream().filter(x -> x.getId().equals(visitId)).findFirst().orElse(null);
        return open != null ? open : visitResponse(v, null, today(ownerId)); // done: it left the list
    }

    @Override
    @Transactional
    public void deleteVisit(UUID ownerId, UUID visitId) {
        CollectionVisit v = ownVisit(ownerId, visitId);
        v.setIsActive(false);
        v.setDeletedAt(Instant.now());
        visitRepository.save(v);
    }

    private CollectionVisit ownVisit(UUID ownerId, UUID visitId) {
        requireSelf(ownerId);
        return visitRepository.findById(visitId)
                .filter(v -> v.getOwnerId().equals(ownerId) && Boolean.TRUE.equals(v.getIsActive()) && v.getDoneAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Visit could not be found"));
    }

    private static CollectionVisitResponse find(List<CollectionVisitResponse> visits, UUID id) {
        return visits.stream().filter(v -> v.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Visit could not be found"));
    }

    private static void requireSelf(UUID ownerId) {
        if (!ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own collections");
        }
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
