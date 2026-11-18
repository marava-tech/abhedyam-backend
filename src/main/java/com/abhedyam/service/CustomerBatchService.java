package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;
import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.SaleCreateRequest;
import com.abhedyam.dto.SaleItemRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.service.interfaces.ICustomerBatchService;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Bring in old records: customers from contacts or a typed list, each with an optional old balance (specs/customers-batch.md). */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerBatchService implements ICustomerBatchService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final SaleService saleService;

    @Override
    public CustomerBatchResult save(UUID ownerId, CustomerBatchRequest request, boolean dryRun) {
        if (ownerId == null || !ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only add customers to your own shop");
        }
        String key = request.getIdempotencyKey().trim();
        String label = request.getLabel() == null || request.getLabel().isBlank() ? "Old balance" : request.getLabel().trim();

        int saved = 0, newCustomers = 0, withAmount = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<CustomerBatchResult.Problem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            int rowNo = i + 1;
            CustomerBatchRequest.Row row = request.getRows().get(i);
            try {
                Checked c = check(row);
                if (!seen.add(c.matchKey())) throw new RowProblem("DUPLICATE");

                Optional<Customer> existing = c.phone() != null
                        ? customerRepository.findByPhoneNormalized(c.phone())
                        : customerRepository.findFirstByOwnerIdAndNameIgnoreCase(ownerId, c.name());
                if (existing.isPresent() && !ownerId.equals(existing.get().getOwnerId())) throw new RowProblem("PHONE_TAKEN");

                if (!dryRun) {
                    Customer customer = existing.orElseGet(() -> {
                        CustomerCreateRequest create = new CustomerCreateRequest();
                        create.setName(c.name());
                        create.setPhone(c.phone());
                        create.setVillage(blankToNull(row.getVillage()));
                        return customerService.create(create);
                    });
                    if (c.owes().signum() > 0) saleService.createSale(oldBalance(customer.getId(), label, c, key + ":" + rowNo));
                }
                saved++;
                if (existing.isEmpty()) newCustomers++;
                if (c.owes().signum() > 0) {
                    withAmount++;
                    total = total.add(c.owes());
                }
            } catch (RowProblem p) {
                problems.add(new CustomerBatchResult.Problem(rowNo, p.code));
            } catch (Exception e) {
                log.warn("Batch row {} for owner {} failed: {}", rowNo, ownerId, e.getMessage());
                problems.add(new CustomerBatchResult.Problem(rowNo, "FAILED"));
            }
        }
        log.info("Customer batch for owner {} (dryRun={}): {} saved, {} new, {} problems", ownerId, dryRun, saved, newCustomers, problems.size());
        return CustomerBatchResult.builder().saved(saved).newCustomers(newCustomers).withAmount(withAmount)
                .totalOwes(total).problems(problems).build();
    }

    /** Validates one row; the match key is the phone, else the name ignoring case (the same rule as the file import). */
    static Checked check(CustomerBatchRequest.Row row) {
        String name = blankToNull(row.getName());
        if (name == null) throw new RowProblem("NO_NAME");
        String phone;
        try {
            phone = BulkImportService.cleanPhoneText(row.getPhone());
        } catch (BusinessException e) {
            throw new RowProblem("BAD_PHONE");
        }
        BigDecimal owes = row.getOwes() == null ? BigDecimal.ZERO : row.getOwes();
        if (owes.signum() < 0) throw new RowProblem("BAD_AMOUNT");
        Instant due = null;
        if (blankToNull(row.getDueDate()) != null) {
            try {
                due = LocalDate.parse(row.getDueDate().trim()).atStartOfDay(IST).toInstant();
            } catch (DateTimeParseException e) {
                throw new RowProblem("BAD_DATE");
            }
        }
        return new Checked(name, phone, owes, due, phone != null ? "phone:" + phone : "name:" + name.toLowerCase());
    }

    private static SaleCreateRequest oldBalance(UUID customerId, String label, Checked c, String clientKey) {
        SaleItemRequest line = new SaleItemRequest();
        line.setProductName(label);
        line.setPrice(c.owes());
        line.setAmountOnly(true);
        SaleCreateRequest sale = new SaleCreateRequest();
        sale.setCustomerId(customerId);
        sale.setItems(List.of(line));
        sale.setDueDate(c.due());
        sale.setIdempotencyKey(clientKey); // a retry finds this sale instead of adding it twice
        return sale;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    record Checked(String name, String phone, BigDecimal owes, Instant due, String matchKey) {
    }

    static class RowProblem extends RuntimeException {
        final String code;

        RowProblem(String code) {
            super(code, null, false, false);
            this.code = code;
        }
    }
}
