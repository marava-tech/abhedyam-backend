package com.abhedyam.service;

import com.abhedyam.dto.BulkImportResult;
import com.abhedyam.dto.SaleDetailResponse;
import com.abhedyam.model.Customer;
import com.abhedyam.model.SaleItem;
import com.abhedyam.repository.CustomerRepository;
import com.abhedyam.security.UserPrincipal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkImportDryRunTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private SaleService saleService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private BulkImportService bulkImportService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(ownerId, "9999999999");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private byte[] createSampleWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Import Data");

            // Row 0 & 1: headers / instruction rows
            sheet.createRow(0);
            sheet.createRow(1);

            // Row 2: Valid row 1 (Customer Alice)
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Alice");
            row2.createCell(1).setCellValue("9876543210");
            row2.createCell(2).setCellValue("Warangal");
            row2.createCell(3).setCellValue("Rice");
            row2.createCell(4).setCellValue(1000);
            row2.createCell(5).setCellValue(500);
            row2.createCell(6).setCellValue("1/11/2026");
            row2.createCell(7).setCellValue("15/11/2026");

            // Row 3: Valid row 2 (Same Customer Alice, second purchase)
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("Alice");
            row3.createCell(1).setCellValue("9876543210");
            row3.createCell(2).setCellValue("Warangal");
            row3.createCell(3).setCellValue("Sugar");
            row3.createCell(4).setCellValue(200);
            row3.createCell(5).setCellValue(200);
            row3.createCell(6).setCellValue("2/11/2026");
            row3.createCell(7).setCellValue("15/11/2026");

            // Row 4: Invalid row (Amount paid 1000 > Product price 500)
            Row row4 = sheet.createRow(4);
            row4.createCell(0).setCellValue("Bob");
            row4.createCell(1).setCellValue("9876543211");
            row4.createCell(2).setCellValue("Hyderabad");
            row4.createCell(3).setCellValue("Oil");
            row4.createCell(4).setCellValue(500);
            row4.createCell(5).setCellValue(1000);
            row4.createCell(6).setCellValue("3/11/2026");
            row4.createCell(7).setCellValue("15/11/2026");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void dryRunReturnsValidationCountsWithoutMutatingDatabase() throws Exception {
        byte[] bytes = createSampleWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        when(customerRepository.findByPhoneNormalized(any())).thenReturn(Optional.empty());

        BulkImportResult result = bulkImportService.importData(ownerId, file, true);

        assertThat(result.getCustomersCreated()).isEqualTo(1); // Deduped: Alice counted once
        assertThat(result.getSalesImported()).isEqualTo(2);
        assertThat(result.getPaymentsRecorded()).isEqualTo(2);
        assertThat(result.getRowsSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("Row 5: Amount Paid cannot exceed Product Price");

        // Verify zero writes occurred
        verify(customerService, never()).create(any());
        verify(saleService, never()).createSale(any());
        verify(paymentService, never()).createImportedPayment(any(), any(), any());
    }

    @Test
    void nonDryRunExecutesWrites() throws Exception {
        byte[] bytes = createSampleWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        Customer mockCustomer = new Customer();
        mockCustomer.setId(UUID.randomUUID());
        mockCustomer.setOwnerId(ownerId);

        when(customerRepository.findByPhoneNormalized(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockCustomer));
        when(customerService.create(any())).thenReturn(mockCustomer);

        SaleItem itemResponse = new SaleItem();
        itemResponse.setId(UUID.randomUUID());

        SaleDetailResponse saleResponse = new SaleDetailResponse();
        saleResponse.setTransactionId("tx-123");
        saleResponse.setItems(List.of(itemResponse));

        when(saleService.createSale(any())).thenReturn(saleResponse);

        BulkImportResult result = bulkImportService.importData(ownerId, file, false);

        assertThat(result.getCustomersCreated()).isEqualTo(1);
        assertThat(result.getSalesImported()).isEqualTo(2);
        assertThat(result.getPaymentsRecorded()).isEqualTo(2);
        assertThat(result.getRowsSkipped()).isEqualTo(1);

        // Verify writes occurred
        verify(customerService).create(any());
    }
}
