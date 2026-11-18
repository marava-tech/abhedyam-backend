package com.abhedyam.service;

import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;
import com.abhedyam.dto.CustomerCreateRequest;
import com.abhedyam.dto.SaleCreateRequest;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Customer;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** specs/customers-batch.md */
@ExtendWith(MockitoExtension.class)
class CustomerBatchTest {

    @Mock CustomerRepository customerRepository;
    @Mock CustomerService customerService;
    @Mock SaleService saleService;
    @InjectMocks CustomerBatchService batch;

    UUID owner = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(owner, "9999999999"), null, Collections.emptyList()));
        lenient().when(customerRepository.findByPhoneNormalized(anyString())).thenReturn(Optional.empty());
        lenient().when(customerRepository.findFirstByOwnerIdAndNameIgnoreCase(eq(owner), anyString())).thenReturn(Optional.empty());
        lenient().when(customerService.create(any())).thenAnswer(i -> customer(owner, ((CustomerCreateRequest) i.getArgument(0)).getName()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    static Customer customer(UUID ownerId, String name) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setOwnerId(ownerId);
        c.setName(name);
        return c;
    }

    static CustomerBatchRequest.Row row(String name, String phone, String owes, String due) {
        CustomerBatchRequest.Row r = new CustomerBatchRequest.Row();
        r.setName(name);
        r.setPhone(phone);
        r.setOwes(owes == null ? null : new BigDecimal(owes));
        r.setDueDate(due);
        return r;
    }

    static CustomerBatchRequest request(CustomerBatchRequest.Row... rows) {
        CustomerBatchRequest r = new CustomerBatchRequest();
        r.setIdempotencyKey("k1");
        r.setLabel("పాత బాకీ");
        r.setRows(new ArrayList<>(List.of(rows)));
        return r;
    }

    @Test
    void newCustomersGetAnOldBalanceSaleInTheOwnersWords_KeyedPerRowSoARetryFindsIt() {
        CustomerBatchResult r = batch.save(owner, request(
                row("Ramesh", "98480 12345", "4000", "2026-12-01"),
                row("Lakshmi", null, null, null)), false);

        assertThat(r.getSaved()).isEqualTo(2);
        assertThat(r.getNewCustomers()).isEqualTo(2);
        assertThat(r.getWithAmount()).isEqualTo(1);
        assertThat(r.getTotalOwes()).isEqualByComparingTo("4000");
        assertThat(r.getProblems()).isEmpty();

        ArgumentCaptor<SaleCreateRequest> sale = ArgumentCaptor.forClass(SaleCreateRequest.class);
        verify(saleService, times(1)).createSale(sale.capture());
        assertThat(sale.getValue().getIdempotencyKey()).isEqualTo("k1:1");
        assertThat(sale.getValue().getItems().get(0).getAmountOnly()).isTrue();
        assertThat(sale.getValue().getItems().get(0).getProductName()).isEqualTo("పాత బాకీ");
        assertThat(sale.getValue().getDueDate()).isNotNull();
    }

    @Test
    void customerAlreadyInTheShop_IsReusedNotCreated() {
        Customer ramesh = customer(owner, "Ramesh");
        when(customerRepository.findByPhoneNormalized("+919848012345")).thenReturn(Optional.of(ramesh));

        CustomerBatchResult r = batch.save(owner, request(row("Ramesh", "9848012345", "500", null)), false);

        assertThat(r.getSaved()).isEqualTo(1);
        assertThat(r.getNewCustomers()).isZero();
        verify(customerService, never()).create(any());
        ArgumentCaptor<SaleCreateRequest> sale = ArgumentCaptor.forClass(SaleCreateRequest.class);
        verify(saleService).createSale(sale.capture());
        assertThat(sale.getValue().getCustomerId()).isEqualTo(ramesh.getId());
    }

    @Test
    void badRowsAreSkippedWithACode_TheRestSave() {
        when(customerRepository.findByPhoneNormalized("+919000000001")).thenReturn(Optional.of(customer(UUID.randomUUID(), "X")));

        CustomerBatchResult r = batch.save(owner, request(
                row(" ", null, "10", null),               // 1 NO_NAME
                row("A", "12345", null, null),            // 2 BAD_PHONE
                row("B", "9000000001", null, null),       // 3 PHONE_TAKEN (another shop's customer)
                row("C", null, "-5", null),               // 4 BAD_AMOUNT
                row("D", null, null, "1/12/2026"),        // 5 BAD_DATE
                row("Good", null, "100", null),           // 6 saved
                row("good", null, "100", null)), false);  // 7 DUPLICATE of 6 by name

        assertThat(r.getProblems()).extracting(CustomerBatchResult.Problem::code)
                .containsExactly("NO_NAME", "BAD_PHONE", "PHONE_TAKEN", "BAD_AMOUNT", "BAD_DATE", "DUPLICATE");
        assertThat(r.getProblems()).extracting(CustomerBatchResult.Problem::row).containsExactly(1, 2, 3, 4, 5, 7);
        assertThat(r.getSaved()).isEqualTo(1);
        assertThat(r.getTotalOwes()).isEqualByComparingTo("100");
    }

    @Test
    void dryRun_CountsButSavesNothing() {
        CustomerBatchResult r = batch.save(owner, request(row("Ramesh", null, "4000", null), row("Ramesh", null, "1", null)), true);

        assertThat(r.getSaved()).isEqualTo(1);
        assertThat(r.getProblems()).extracting(CustomerBatchResult.Problem::code).containsExactly("DUPLICATE");
        verify(customerService, never()).create(any());
        verify(saleService, never()).createSale(any());
    }

    @Test
    void aSaveThatFailsUnexpectedly_IsFailedForThatRowOnly() {
        when(saleService.createSale(any())).thenThrow(new IllegalStateException("db down")).thenReturn(null);

        CustomerBatchResult r = batch.save(owner, request(row("A", null, "1", null), row("B", null, "2", null)), false);

        assertThat(r.getProblems()).containsExactly(new CustomerBatchResult.Problem(1, "FAILED"));
        assertThat(r.getSaved()).isEqualTo(1);
    }

    @Test
    void anotherOwner_IsRefused() {
        assertThatThrownBy(() -> batch.save(UUID.randomUUID(), request(row("A", null, null, null)), false))
                .isInstanceOf(BusinessException.class);
    }
}
