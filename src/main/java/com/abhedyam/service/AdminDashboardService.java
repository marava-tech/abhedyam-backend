package com.abhedyam.service;

import com.abhedyam.dto.*;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.*;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.model.enums.ReferralStatus;
import com.abhedyam.service.interfaces.IAdminDashboardService;
import com.abhedyam.service.interfaces.IBillingService;
import com.abhedyam.service.interfaces.IGrowthAdminService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Spec: specs/admin-dashboard.md.
 * ponytail: loads every owner and uses grouped counts; move filters/paging into SQL past a few thousand shops.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService implements IAdminDashboardService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final List<String> STATUSES = List.of("TRIAL", "PAYING", "READ_ONLY", "DELETING", "CLOSED");

    @PersistenceContext
    private EntityManager em;

    private final IBillingService billingService;
    private final IGrowthAdminService growthAdminService;

    /** First match wins: closed, asked to delete, paying on Play, access left, read-only. */
    static String status(boolean active, boolean deleting, String playProductId, Instant accessUntil, Instant now) {
        if (!active) return "CLOSED";
        if (deleting) return "DELETING";
        if (playProductId != null) return "PAYING";
        if (accessUntil != null && accessUntil.isAfter(now)) return "TRIAL";
        return "READ_ONLY";
    }

    static long daysLeft(Instant accessUntil, Instant now) {
        if (accessUntil == null || !accessUntil.isAfter(now)) return 0;
        return (long) Math.ceil(Duration.between(now, accessUntil).toHours() / 24.0);
    }

    // ---------- overview ----------

    @Override
    @Transactional(readOnly = true)
    public AdminOverview overview() {
        Instant now = Instant.now();
        List<Owner> owners = em.createQuery("select o from Owner o", Owner.class).getResultList();
        List<AdminShopRow> rows = rows(owners, null, now);

        Map<String, Long> byStatus = rows.stream().collect(Collectors.groupingBy(AdminShopRow::status, Collectors.counting()));
        var shops = new AdminOverview.Shops(rows.size(), byStatus.getOrDefault("TRIAL", 0L), byStatus.getOrDefault("PAYING", 0L),
                byStatus.getOrDefault("READ_ONLY", 0L), byStatus.getOrDefault("DELETING", 0L), byStatus.getOrDefault("CLOSED", 0L));
        Map<String, Long> payingByProduct = rows.stream().filter(r -> r.status().equals("PAYING"))
                .collect(Collectors.groupingBy(AdminShopRow::playProductId, TreeMap::new, Collectors.counting()));
        long paidEver = owners.stream().filter(o -> o.getFirstPaidAt() != null).count();

        LocalDate today = LocalDate.now(IST);
        Map<LocalDate, Long> perDay = owners.stream().filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().atZone(IST).toLocalDate(), Collectors.counting()));
        List<AdminOverview.Day> byDay = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            byDay.add(new AdminOverview.Day(d.toString(), perDay.getOrDefault(d, 0L)));
        }
        long signups7d = byDay.subList(23, 30).stream().mapToLong(AdminOverview.Day::count).sum();
        long signups30d = byDay.stream().mapToLong(AdminOverview.Day::count).sum();

        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
        Set<UUID> active7d = new HashSet<>(em.createQuery(
                "select distinct s.ownerId from SaleItem s where s.createdAt >= :t", UUID.class).setParameter("t", weekAgo).getResultList());
        active7d.addAll(em.createQuery(
                "select distinct p.ownerId from Payment p where p.createdAt >= :t", UUID.class).setParameter("t", weekAgo).getResultList());

        long trialsEnding = rows.stream().filter(r -> r.status().equals("TRIAL") && r.daysLeft() <= 3).count();
        long callsDue = growthAdminService.trialCalls().stream().filter(c -> c.calledAt() == null).count();

        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        List<Owner> cohort = owners.stream().filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(monthAgo)).toList();
        Map<UUID, Long> customers = counts("select c.ownerId, count(c) from Customer c", null);
        Map<UUID, Long> sales = counts("select s.ownerId, count(distinct s.transactionId) from SaleItem s", null);
        var funnel = new AdminOverview.Funnel(cohort.size(),
                cohort.stream().filter(o -> customers.getOrDefault(o.getId(), 0L) > 0).count(),
                cohort.stream().filter(o -> sales.getOrDefault(o.getId(), 0L) > 0).count(),
                cohort.stream().filter(o -> o.getFirstPaidAt() != null).count());

        Long partnersOwed = em.createQuery(
                "select coalesce(sum(e.rupees), 0) from PartnerEarning e where e.paidAt is null", Long.class).getSingleResult();
        Long cashOwed = em.createQuery(
                "select count(r) from Referral r where r.rewardCash = true and r.cashPaidAt is null", Long.class).getSingleResult();

        return new AdminOverview(shops, payingByProduct, Math.max(0, BillingService.FOUNDING_SEATS - paidEver),
                perDay.getOrDefault(today, 0L), signups7d, signups30d, byDay, active7d.size(), trialsEnding, callsDue, funnel,
                new AdminOverview.Owed(partnersOwed, cashOwed * ReferralService.REFERRER_REWARD_RUPEES));
    }

    // ---------- shops ----------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminShopRow> shops(String q, String status, String sort, int page, int size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException("INVALID_STATUS", "Status must be one of " + STATUSES);
        }
        Instant now = Instant.now();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<AdminShopRow> rows = rows(em.createQuery("select o from Owner o", Owner.class).getResultList(), null, now).stream()
                .filter(r -> status == null || status.isBlank() || r.status().equals(status))
                .filter(r -> needle.isEmpty() || matches(r, needle))
                .sorted(order(sort))
                .toList();
        int pageSize = Math.max(1, Math.min(size, 100));
        int from = Math.min(Math.max(page, 0) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        int totalPages = (rows.size() + pageSize - 1) / pageSize;
        return new PageResponse<>(rows.subList(from, to), Math.max(page, 0), pageSize, rows.size(), totalPages,
                to < rows.size(), from > 0);
    }

    private static boolean matches(AdminShopRow r, String needle) {
        return Stream.of(r.name(), r.businessName(), r.email(), r.phone())
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static Comparator<AdminShopRow> order(String sort) {
        Comparator<Instant> newestFirst = Comparator.nullsLast(Comparator.reverseOrder());
        return switch (sort == null ? "newest" : sort) {
            case "lastSeen" -> Comparator.comparing(AdminShopRow::lastSeenAt, newestFirst);
            case "customers" -> Comparator.comparingLong(AdminShopRow::customers).reversed();
            case "daysLeft" -> Comparator.comparing((AdminShopRow r) -> r.daysLeft() == 0).thenComparingLong(AdminShopRow::daysLeft);
            default -> Comparator.comparing(AdminShopRow::createdAt, newestFirst);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public AdminShopDetail shop(UUID ownerId) {
        Owner owner = em.find(Owner.class, ownerId);
        if (owner == null) throw new ResourceNotFoundException("Shop not found");
        Instant now = Instant.now();
        AdminShopRow row = rows(List.of(owner), ownerId, now).get(0);

        long sales = one("select count(distinct s.transactionId) from SaleItem s where s.ownerId = :o", ownerId);
        long payments = one("select count(p) from Payment p where p.ownerId = :o and p.status = :ok", ownerId);
        BigDecimal collected = em.createQuery("select coalesce(sum(p.amount), 0) from Payment p where p.ownerId = :o and p.status = :ok"
                        + " and (p.writeOff is null or p.writeOff = false) and p.timestamp >= :t", BigDecimal.class)
                .setParameter("o", ownerId).setParameter("ok", PaymentStatus.SUCCESS)
                .setParameter("t", now.minus(30, ChronoUnit.DAYS)).getSingleResult();

        OwnerSettings settings = em.createQuery("select s from OwnerSettings s where s.ownerId = :o", OwnerSettings.class)
                .setParameter("o", ownerId).getResultStream().findFirst().orElse(null);
        Map<String, Object> flags = settings == null || settings.getOtherFlags() == null ? Map.of() : settings.getOtherFlags();
        @SuppressWarnings("unchecked")
        Map<String, Object> setup = flags.get("business") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;

        List<ShopMember> members = em.createQuery("select m from ShopMember m where m.ownerId = :o order by m.createdAt", ShopMember.class)
                .setParameter("o", ownerId).getResultList();
        Map<UUID, String> memberNames = members.stream().collect(Collectors.toMap(ShopMember::getId, m -> Objects.toString(m.getName(), ""), (a, b) -> a));
        List<AdminShopDetail.Device> devices = em.createQuery(
                        "select s from OwnerSession s where s.ownerId = :o and s.revokedAt is null order by s.lastSeenAt desc", OwnerSession.class)
                .setParameter("o", ownerId).getResultList().stream()
                .map(s -> new AdminShopDetail.Device(s.getDeviceName(), s.getMemberId() == null ? null : memberNames.get(s.getMemberId()), s.getLastSeenAt()))
                .toList();

        List<Referral> referred = em.createQuery("select r from Referral r where r.referrerOwnerId = :o order by r.createdAt desc", Referral.class)
                .setParameter("o", ownerId).getResultList();
        Map<UUID, String> friendNames = names(referred.stream().map(Referral::getRefereeOwnerId).toList());
        List<AdminShopDetail.Friend> friends = referred.stream()
                .map(r -> new AdminShopDetail.Friend(r.getRefereeOwnerId(), friendNames.get(r.getRefereeOwnerId()), r.getCreatedAt(), r.getStatus() == ReferralStatus.PAID))
                .toList();

        List<AdminShopDetail.Call> calls = em.createQuery("select c from FounderCall c where c.ownerId = :o order by c.createdAt desc", FounderCall.class)
                .setParameter("o", ownerId).getResultList().stream()
                .map(c -> new AdminShopDetail.Call(c.getStage(), c.getNote(), c.getCreatedAt())).toList();
        List<AdminShopDetail.FeedbackItem> feedback = em.createQuery("select f from Feedback f where f.userId = :o order by f.createdAt desc", Feedback.class)
                .setParameter("o", ownerId).getResultList().stream()
                .map(f -> new AdminShopDetail.FeedbackItem(Objects.toString(f.getCategory(), null), f.getIssueDescription(), f.getImageUrl(), f.getCreatedAt()))
                .toList();

        return new AdminShopDetail(row, row.customers(), sales, payments, collected.longValue(), setup,
                settings == null ? null : settings.getVillageModeEnabled(),
                settings == null ? null : settings.getLocationToolsEnabled(),
                settings == null ? null : settings.getDeletionRequestedAt(),
                devices,
                members.stream().map(m -> new AdminShopDetail.Member(m.getName(), m.getEmail(), String.valueOf(m.getRole()),
                        String.valueOf(m.getStatus()), m.getJoinedAt())).toList(),
                friends, calls, feedback);
    }

    @Override
    @Transactional
    public void grantDays(UUID ownerId, int days) {
        billingService.grantDays(ownerId, days);
    }

    @Override
    @Transactional
    public void endAccess(UUID ownerId) {
        billingService.endAccess(ownerId);
    }

    // ---------- referrals ----------

    @Override
    @Transactional(readOnly = true)
    public List<AdminReferralRow> referrals() {
        List<Referral> all = em.createQuery("select r from Referral r order by r.createdAt desc", Referral.class).getResultList();
        Set<UUID> ids = new HashSet<>();
        all.forEach(r -> { ids.add(r.getReferrerOwnerId()); ids.add(r.getRefereeOwnerId()); });
        Map<UUID, String> names = names(ids);
        return all.stream().map(r -> {
            boolean cash = Boolean.TRUE.equals(r.getRewardCash());
            return new AdminReferralRow(r.getId(),
                    new AdminReferralRow.ShopRef(r.getReferrerOwnerId(), names.get(r.getReferrerOwnerId())),
                    new AdminReferralRow.ShopRef(r.getRefereeOwnerId(), names.get(r.getRefereeOwnerId())),
                    String.valueOf(r.getStatus()), r.getCreatedAt(), r.getRewardedAt(), cash,
                    cash ? ReferralService.REFERRER_REWARD_RUPEES : 0, r.getCashPaidAt());
        }).toList();
    }

    @Override
    @Transactional
    public void markReferralCashPaid(UUID referralId) {
        Referral r = em.find(Referral.class, referralId);
        if (r == null) throw new ResourceNotFoundException("Referral not found");
        if (!Boolean.TRUE.equals(r.getRewardCash())) {
            throw new BusinessException("NO_CASH_REWARD", "This reward was free days, not cash");
        }
        if (r.getCashPaidAt() == null) r.setCashPaidAt(Instant.now());
    }

    // ---------- helpers ----------

    /** Rows for these owners; onlyOwner limits the grouped counts to one shop (detail page). */
    private List<AdminShopRow> rows(List<Owner> owners, UUID onlyOwner, Instant now) {
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        Map<UUID, Long> customers = counts("select c.ownerId, count(c) from Customer c", onlyOwner);
        Map<UUID, Long> sales30d = counts("select s.ownerId, count(distinct s.transactionId) from SaleItem s where s.createdAt >= :t", onlyOwner, monthAgo);
        Map<UUID, Instant> lastSeen = latest("select s.ownerId, max(s.lastSeenAt) from OwnerSession s", onlyOwner);
        Map<UUID, Instant> lastSale = latest("select s.ownerId, max(s.createdAt) from SaleItem s", onlyOwner);
        Map<UUID, Instant> lastPayment = latest("select p.ownerId, max(p.createdAt) from Payment p", onlyOwner);
        Set<UUID> deleting = new HashSet<>(em.createQuery(
                "select s.ownerId from OwnerSettings s where s.deletionRequestedAt is not null", UUID.class).getResultList());
        Map<String, String> partnerNames = em.createQuery("select p from Partner p", Partner.class).getResultList().stream()
                .collect(Collectors.toMap(p -> p.getId().toString(), Partner::getName, (a, b) -> a));
        Map<UUID, String> referrerNames = names(owners.stream().map(Owner::getReferredBy).filter(Objects::nonNull)
                .map(AdminDashboardService::uuid).filter(Objects::nonNull).toList());

        List<AdminShopRow> rows = new ArrayList<>(owners.size());
        for (Owner o : owners) {
            UUID id = o.getId();
            AdminShopRow.Source source = null;
            UUID referrer = uuid(o.getReferredBy());
            if (referrer != null) source = new AdminShopRow.Source("REFERRAL", referrer, referrerNames.get(referrer));
            else if (o.getPartnerId() != null) source = new AdminShopRow.Source("PARTNER", uuid(o.getPartnerId()), partnerNames.get(o.getPartnerId()));
            rows.add(new AdminShopRow(id, o.getName(), o.getBusinessName(), o.getEmail(), o.getPhone(), o.getCreatedAt(),
                    status(!Boolean.FALSE.equals(o.getIsActive()), deleting.contains(id), o.getPlayProductId(), o.getAccessUntil(), now),
                    o.getAccessUntil(), daysLeft(o.getAccessUntil(), now), o.getPlayProductId(), o.getFirstPaidAt(),
                    customers.getOrDefault(id, 0L), sales30d.getOrDefault(id, 0L), lastSeen.get(id),
                    later(lastSale.get(id), lastPayment.get(id)), source));
        }
        return rows;
    }

    /** "select x.ownerId, count(..) from X x [where ..]" grouped by owner, optionally for one owner only. */
    private Map<UUID, Long> counts(String select, UUID onlyOwner, Instant... since) {
        return grouped(select, onlyOwner, since, r -> ((Number) r[1]).longValue());
    }

    private Map<UUID, Instant> latest(String select, UUID onlyOwner) {
        return grouped(select, onlyOwner, new Instant[0], r -> (Instant) r[1]);
    }

    private <T> Map<UUID, T> grouped(String select, UUID onlyOwner, Instant[] since, Function<Object[], T> value) {
        String alias = select.substring(select.indexOf(' ') + 1, select.indexOf('.'));
        String jpql = select + (onlyOwner == null ? "" : (select.contains(" where ") ? " and " : " where ") + alias + ".ownerId = :o")
                + " group by " + alias + ".ownerId";
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        if (onlyOwner != null) query.setParameter("o", onlyOwner);
        if (since.length > 0) query.setParameter("t", since[0]);
        Map<UUID, T> out = new HashMap<>();
        for (Object[] r : query.getResultList()) {
            T v = value.apply(r);
            if (r[0] != null && v != null) out.put((UUID) r[0], v);
        }
        return out;
    }

    private long one(String jpql, UUID ownerId) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class).setParameter("o", ownerId);
        if (jpql.contains(":ok")) q.setParameter("ok", PaymentStatus.SUCCESS);
        return q.getSingleResult();
    }

    /** Shop name (business name, else owner name) for these owner ids. */
    private Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return em.createQuery("select o from Owner o where o.id in :ids", Owner.class).setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(Owner::getId, AdminDashboardService::shopName, (a, b) -> a));
    }

    static String shopName(Owner o) {
        return o.getBusinessName() != null && !o.getBusinessName().isBlank() ? o.getBusinessName() : o.getName();
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
