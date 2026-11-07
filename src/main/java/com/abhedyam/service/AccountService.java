package com.abhedyam.service;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.*;
import com.abhedyam.repository.*;
import com.abhedyam.service.interfaces.IAccountService;
import com.abhedyam.util.CsvUtil;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(IST);

    /** Tables keyed by owner_id that are removed with the owner (subscriptions are kept: billing records). */
    private static final List<Class<?>> OWNER_SCOPED = List.of(
            SaleItem.class, Payment.class, Product.class, Inventory.class, Reminder.class, Note.class,
            Document.class, UPIAccount.class, Audit.class, Notification.class, OwnerSettings.class,
            DailyStats.class, TopProduct.class);

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate tx;
    private final OwnerRepository ownerRepository;
    private final CustomerRepository customerRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final LocationDetailsRepository locationDetailsRepository;
    private final OwnerSettingsRepository settingsRepository;

    @Override
    public Instant requestDeletion(UUID ownerId) {
        return tx.execute(s -> {
            ownerRepository.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            OwnerSettings st = settingsRepository.findByOwnerId(ownerId).orElseGet(() -> {
                OwnerSettings n = new OwnerSettings();
                n.setOwnerId(ownerId);
                return n;
            });
            if (st.getDeletionRequestedAt() == null) { // asking twice must not push the date out
                st.setDeletionRequestedAt(Instant.now());
                st.setDeletionExportSentAt(null);
                settingsRepository.save(st);
                log.info("Account deletion requested: owner {}", ownerId);
            }
            return st.getDeletionRequestedAt().plus(DELETION_GRACE);
        });
    }

    @Override
    public void cancelDeletion(UUID ownerId) {
        tx.executeWithoutResult(s -> settingsRepository.findByOwnerId(ownerId).ifPresent(st -> {
            if (st.getDeletionRequestedAt() == null) return;
            st.setDeletionRequestedAt(null);
            st.setDeletionExportSentAt(null);
            settingsRepository.save(st);
            log.info("Account deletion cancelled: owner {}", ownerId);
        }));
    }

    @Override
    public Optional<Instant> deletionScheduledFor(UUID ownerId) {
        return settingsRepository.findByOwnerId(ownerId)
                .map(OwnerSettings::getDeletionRequestedAt)
                .map(t -> t.plus(DELETION_GRACE));
    }

    @Override
    public void disableAccount(UUID ownerId) {
        String firebaseUid = tx.execute(status -> {
            Owner owner = ownerRepository.findById(ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (!Boolean.TRUE.equals(owner.getIsActive())) return null; // already disabled
            Instant now = Instant.now();

            // Nothing may reach this shop's customers any more (reminders) or be pushed to anyone.
            for (Class<?> c : List.of(Reminder.class, Notification.class, OwnerSettings.class)) {
                em.createQuery("delete from " + c.getSimpleName() + " e where e.ownerId = :o")
                        .setParameter("o", ownerId).executeUpdate();
            }
            List<Customer> customers = customerRepository.findByOwnerId(ownerId);
            List<UUID> customerIds = customers.stream().map(Customer::getId).collect(Collectors.toList());
            if (!customerIds.isEmpty()) {
                for (Class<?> c : List.of(FcmDetails.class, Notification.class)) {
                    em.createQuery("delete from " + c.getSimpleName() + " e where e.userId in :ids")
                            .setParameter("ids", customerIds).executeUpdate();
                }
            }
            em.createQuery("delete from FcmDetails e where e.userId = :o").setParameter("o", ownerId).executeUpdate();

            // Customer phone numbers are globally unique: free them (the plain phone stays on the row) so the same
            // person, or another shop, can add that customer again.
            for (Customer c : customers) {
                c.setIsActive(false);
                c.setDeletedAt(now);
                c.setPhoneNormalized(null);
            }
            customerRepository.saveAll(customers);

            String uid = owner.getFirebaseUid();
            owner.setFormerEmail(owner.getEmail());
            owner.setEmail(null);
            owner.setFirebaseUid(null);
            owner.setPhoneNormalized(null);
            owner.setPublicSlug(null);
            owner.setPublicListingEnabled(false);
            owner.setIsActive(false);
            owner.setDeletedAt(now);
            ownerRepository.save(owner);
            return uid;
        });

        // The Google sign-in record goes too; a new sign-in with the same Gmail makes a fresh one. A failure only means
        // the old Firebase user stays, which still works because the database row no longer holds its id.
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            try {
                FirebaseAuth.getInstance().deleteUser(firebaseUid);
            } catch (Exception e) {
                log.warn("Firebase user delete failed for disabled owner {}: {}", ownerId, e.getMessage());
            }
        }
        log.info("Account disabled: owner {}", ownerId);
    }

    @Override
    public void deleteAccount(UUID ownerId) {
        String firebaseUid = tx.execute(status -> {
            Owner owner = ownerRepository.findById(ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            String uid = owner.getFirebaseUid();

            for (Class<?> c : OWNER_SCOPED) {
                em.createQuery("delete from " + c.getSimpleName() + " e where e.ownerId = :o")
                        .setParameter("o", ownerId).executeUpdate();
            }

            List<Customer> customers = customerRepository.findByOwnerId(ownerId);
            List<UUID> customerIds = customers.stream().map(Customer::getId).collect(Collectors.toList());
            if (!customerIds.isEmpty()) {
                for (Class<?> c : List.of(LocationDetails.class, FcmDetails.class, Notification.class)) {
                    em.createQuery("delete from " + c.getSimpleName() + " e where e.userId in :ids")
                            .setParameter("ids", customerIds).executeUpdate();
                }
                customerRepository.deleteAll(customers);
            }

            for (Class<?> c : List.of(FcmDetails.class, Feedback.class, LocationDetails.class)) {
                em.createQuery("delete from " + c.getSimpleName() + " e where e.userId = :o")
                        .setParameter("o", ownerId).executeUpdate();
            }
            em.flush();
            ownerRepository.delete(owner);
            return uid;
        });

        // After the DB commit; the account is already gone, so a Firebase failure is only logged.
        if (firebaseUid != null && !firebaseUid.isBlank()) {
            try {
                FirebaseAuth.getInstance().deleteUser(firebaseUid);
            } catch (Exception e) {
                log.warn("Firebase user delete failed for deleted owner {}: {}", ownerId, e.getMessage());
            }
        }
        log.info("Account deleted: owner {}", ownerId);
    }

    @Override
    public String exportCsv(UUID ownerId, String type) {
        if (type == null) throw new BusinessException("INVALID_EXPORT_TYPE", "type must be customers, sales or payments");
        List<Customer> customers = customerRepository.findByOwnerId(ownerId);
        Map<UUID, String> names = customers.stream()
                .collect(Collectors.toMap(Customer::getId, c -> Objects.toString(c.getName(), ""), (a, b) -> a));
        StringBuilder out = new StringBuilder();
        switch (type) {
            case "customers" -> {
                Map<UUID, BigDecimal> due = new HashMap<>();
                for (SaleItem s : saleItemRepository.findByOwnerId(ownerId)) {
                    due.merge(s.getCustomerId(), nz(s.getRemainingAmount()), BigDecimal::add);
                }
                Map<UUID, String> villages = new HashMap<>();
                for (LocationDetails l : locationDetailsRepository.findByUserIdIn(customers.stream().map(Customer::getId).toList())) { // all villages, pin or not
                    villages.put(l.getUserId(), s(l.getVillage()));
                }
                out.append(CsvUtil.row(List.of("Name", "Phone", "Village", "Pending amount", "Added on")));
                customers.stream().sorted(Comparator.comparing(Customer::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .forEach(c -> out.append(CsvUtil.row(List.of(
                                s(c.getName()), s(c.getPhone()), villages.getOrDefault(c.getId(), ""),
                                num(due.getOrDefault(c.getId(), BigDecimal.ZERO)), when(c.getCreatedAt())))));
            }
            case "sales" -> {
                Map<UUID, String> products = productRepository.findByOwnerId(ownerId).stream()
                        .collect(Collectors.toMap(Product::getId, p -> Objects.toString(p.getName(), ""), (a, b) -> a));
                out.append(CsvUtil.row(List.of("Date", "Customer", "Item", "Quantity", "Price", "Total", "Still due", "Status", "Due date", "Bill id")));
                saleItemRepository.findByOwnerId(ownerId).stream()
                        .sorted(Comparator.comparing(SaleItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .forEach(i -> out.append(CsvUtil.row(List.of(
                                when(i.getCreatedAt()), names.getOrDefault(i.getCustomerId(), ""),
                                products.getOrDefault(i.getProductId(), ""), num(i.getQuantity()), num(i.getPrice()),
                                num(nz(i.getPrice()).multiply(nz(i.getQuantity()))), num(nz(i.getRemainingAmount())),
                                Objects.toString(i.getStatus(), ""), when(i.getDueDate()), s(i.getTransactionId())))));
            }
            case "payments" -> {
                out.append(CsvUtil.row(List.of("Date", "Customer", "Amount", "Paid by", "Status", "Reference")));
                paymentRepository.findByOwnerId(ownerId).stream()
                        .sorted(Comparator.comparing(Payment::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                        .forEach(p -> out.append(CsvUtil.row(List.of(
                                when(p.getTimestamp()), names.getOrDefault(p.getCustomerId(), ""), num(p.getAmount()),
                                Objects.toString(p.getMedium(), ""), Objects.toString(p.getStatus(), ""), s(p.getReference())))));
            }
            default -> throw new BusinessException("INVALID_EXPORT_TYPE", "type must be customers, sales or payments");
        }
        return out.toString();
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String num(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }
    private static String s(String v) { return v == null ? "" : v; }
    private static String when(Instant t) { return t == null ? "" : STAMP.format(t); }
}
