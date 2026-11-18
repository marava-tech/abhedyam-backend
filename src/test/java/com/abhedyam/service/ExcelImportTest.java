package com.abhedyam.service;

import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;
import com.abhedyam.security.UserPrincipal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** specs/customers-batch.md, "From an Excel file". */
class ExcelImportTest {

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Each inner list is a row; a LocalDate becomes a real date cell, a Number a number cell. */
    static byte[] xlsx(List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Sheet1");
            var dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            for (int r = 0; r < rows.size(); r++) {
                Row row = s.createRow(r);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    Object v = rows.get(r).get(c);
                    if (v == null) continue;
                    var cell = row.createCell(c);
                    if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateStyle);
                    } else cell.setCellValue(v.toString());
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void teluguHeadersInAnyOrderAndEveryCellKind() throws Exception {
        var p = BulkImportService.parse(xlsx(List.of(
                List.of("ఫోన్", "పేరు", "బాకీ", "ఊరు", "చెల్లించే తేదీ"),
                java.util.Arrays.asList(9848012345L, "Ramesh", "₹4,000", "Kondapur", LocalDate.of(2026, 12, 1)),
                java.util.Arrays.asList(null, "Sita", 250, null, "5/1/2027"),
                java.util.Arrays.asList(),
                java.util.Arrays.asList(null, "Bad", "lots", null, null))));

        assertThat(p.rows()).hasSize(2);
        CustomerBatchRequest.Row ramesh = p.rows().get(0);
        assertThat(ramesh.getName()).isEqualTo("Ramesh");
        assertThat(ramesh.getPhone()).isEqualTo("9848012345");
        assertThat(ramesh.getVillage()).isEqualTo("Kondapur");
        assertThat(ramesh.getOwes()).isEqualByComparingTo("4000");
        assertThat(ramesh.getDueDate()).isEqualTo("2026-12-01");
        assertThat(p.rows().get(1).getDueDate()).isEqualTo("2027-01-05");
        assertThat(p.sheetRows()).containsExactly(2, 3);
        assertThat(p.problems()).containsExactly(new CustomerBatchResult.Problem(5, "BAD_AMOUNT"));
    }

    @Test
    void noHeaderRowMeansTemplateOrder() throws Exception {
        var p = BulkImportService.parse(xlsx(List.of(List.of("Ravi", "98480 12345", "Dhone", 100, "2026-12-31"))));
        assertThat(p.rows()).hasSize(1);
        assertThat(p.rows().get(0).getName()).isEqualTo("Ravi");
        assertThat(p.rows().get(0).getDueDate()).isEqualTo("2026-12-31");
        assertThat(p.sheetRows()).containsExactly(1);
    }

    @Test
    void problemsComeBackWithExcelRowNumbersAndTheSameKeyOnARetry() throws Exception {
        UUID owner = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(owner, "9999999999"), null, Collections.emptyList()));
        CustomerBatchService batch = mock(CustomerBatchService.class);
        when(batch.save(eq(owner), any(), eq(false))).thenAnswer(i -> CustomerBatchResult.builder().saved(1).totalOwes(BigDecimal.TEN)
                .problems(List.of(new CustomerBatchResult.Problem(2, "BAD_PHONE")))
                .rows(List.of(new CustomerBatchResult.RowResult(1, "A", BigDecimal.TEN, "ADDED"), new CustomerBatchResult.RowResult(2, "B", null, "BAD_PHONE"))).build());
        BulkImportService excel = new BulkImportService(batch);
        byte[] bytes = xlsx(List.of(List.of("Name", "Phone", "Owes"), List.of("A", "", 10), List.of("B", "123", 5)));
        var file = new MockMultipartFile("file", "old.xlsx", null, bytes);

        CustomerBatchResult result = excel.importData(owner, file, false, "పాత బాకీ");
        excel.importData(owner, file, false, "పాత బాకీ");

        assertThat(result.getProblems()).containsExactly(new CustomerBatchResult.Problem(3, "BAD_PHONE"));
        assertThat(result.getRows()).extracting(CustomerBatchResult.RowResult::row).containsExactly(2, 3);
        ArgumentCaptor<CustomerBatchRequest> sent = ArgumentCaptor.forClass(CustomerBatchRequest.class);
        verify(batch, org.mockito.Mockito.times(2)).save(eq(owner), sent.capture(), eq(false));
        assertThat(sent.getAllValues().get(0).getIdempotencyKey()).isEqualTo(sent.getAllValues().get(1).getIdempotencyKey());
        assertThat(sent.getAllValues().get(0).getLabel()).isEqualTo("పాత బాకీ");
    }

    @Test
    void templateHasTheFiveColumnsInTheOwnersLanguage() throws Exception {
        byte[] t = new BulkImportService(mock(CustomerBatchService.class)).template("hi");
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(t))) {
            Row h = wb.getSheetAt(0).getRow(0);
            assertThat(h.getCell(0).getStringCellValue()).isEqualTo("नाम");
            assertThat(h.getLastCellNum()).isEqualTo((short) 5);
        }
        // and the template's own headers are read back
        assertThat(BulkImportService.parse(t).rows()).isEmpty();
    }
}
