package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CustomerDuesResponse;
import com.abhedyam.dto.KhataLinkResponse;
import com.abhedyam.dto.PaymentDueResponse;
import com.abhedyam.dto.PublicKhataResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Customer;
import com.abhedyam.model.Owner;
import com.abhedyam.model.Payment;
import com.abhedyam.model.Product;
import com.abhedyam.model.SaleItem;
import com.abhedyam.model.UPIAccount;
import com.abhedyam.model.enums.PaymentStatus;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.PaymentRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.SaleItemRepository;
import com.abhedyam.repository.UPIAccountRepository;
import com.abhedyam.service.interfaces.IKhataLinkService;
import com.abhedyam.service.interfaces.IPaymentDueService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KhataLinkService implements IKhataLinkService {

    static final int MAX_ENTRIES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository customerRepository;
    private final OwnerRepository ownerRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final UPIAccountRepository upiAccountRepository;
    private final IPaymentDueService dueService;

    @Value("${app.khata-link-base:https://duesetu.com/k/}")
    private String linkBase = "https://duesetu.com/k/";

    @Override
    @Transactional
    public KhataLinkResponse get(UUID ownerId, UUID customerId) {
        Customer c = ownCustomer(ownerId, customerId);
        if (c.getLinkToken() == null) {
            c.setLinkToken(newToken());
            customerRepository.save(c);
        }
        return response(c.getLinkToken());
    }

    @Override
    @Transactional
    public KhataLinkResponse reset(UUID ownerId, UUID customerId) {
        Customer c = ownCustomer(ownerId, customerId);
        c.setLinkToken(newToken());
        customerRepository.save(c);
        return response(c.getLinkToken());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKhataResponse view(String token) {
        Customer c = token == null || token.isBlank() ? null : customerRepository.findByLinkToken(token).orElse(null);
        Owner owner = c == null || c.getDeletedAt() != null || c.getOwnerId() == null
                ? null : ownerRepository.findById(c.getOwnerId()).orElse(null);
        if (owner == null || owner.getDeletedAt() != null) {
            throw new ResourceNotFoundException("This link no longer works. Ask the shop for a new one");
        }
        UUID ownerId = owner.getId();
        List<SaleItem> sales = saleItemRepository.findByCustomerIdAndOwnerId(c.getId(), ownerId);
        List<Payment> paid = paymentRepository.findByCustomerIdAndOwnerId(c.getId(), ownerId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .toList();

        // Same rule as the owner's customer page: sale total minus successful payments.
        BigDecimal balance = sales.stream().map(KhataLinkService::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(paid.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        CustomerDuesResponse dues = dueService.listForLink(ownerId, c.getId(), balance);
        PaymentDueResponse next = dues.getNext();

        return new PublicKhataResponse(
                owner.getBusinessName() != null && !owner.getBusinessName().isBlank() ? owner.getBusinessName() : owner.getName(),
                owner.getPhone(),
                owner.getImageUrl(),
                c.getName(),
                balance,
                next == null ? null : new PublicKhataResponse.NextDue(next.getAmount(), next.getRemaining(), next.getDueDate(), next.getStatus()),
                dues.getOverdueCount(),
                upiAccountRepository.findByOwnerId(ownerId).map(UPIAccount::getVpa).orElse(null),
                entries(sales, paid),
                Instant.now(),
                owner.getReferralCode()); // not created here: a read; null until the owner opens Invite shops
    }

    /** Sales and successful payments, newest first, at most MAX_ENTRIES. */
    List<PublicKhataResponse.Entry> entries(List<SaleItem> sales, List<Payment> paid) {
        List<UUID> productIds = sales.stream().map(SaleItem::getProductId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> names = productIds.isEmpty() ? Map.of()
                : productRepository.findByIdIn(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        List<PublicKhataResponse.Entry> all = new ArrayList<>();
        sales.forEach(s -> all.add(new PublicKhataResponse.Entry("SALE",
                s.getProductId() == null ? null : names.get(s.getProductId()), total(s), s.getCreatedAt())));
        paid.forEach(p -> all.add(new PublicKhataResponse.Entry(
                Boolean.TRUE.equals(p.getWriteOff()) ? "WRITE_OFF" : "PAYMENT",
                p.getMedium() == null ? null : p.getMedium().name(),
                p.getAmount(),
                p.getTimestamp() != null ? p.getTimestamp() : p.getCreatedAt())));
        return all.stream()
                .sorted(Comparator.comparing(PublicKhataResponse.Entry::at, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ENTRIES)
                .toList();
    }

    private static BigDecimal total(SaleItem s) {
        return s.getPrice().multiply(s.getQuantity() != null ? s.getQuantity() : BigDecimal.ONE);
    }

    private Customer ownCustomer(UUID ownerId, UUID customerId) {
        if (ownerId == null || !ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own customers");
        }
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer could not be found"));
        if (!ownerId.equals(c.getOwnerId()) || c.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this customer");
        }
        return c;
    }

    private KhataLinkResponse response(String token) {
        return new KhataLinkResponse(token, linkBase + token);
    }

    static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
