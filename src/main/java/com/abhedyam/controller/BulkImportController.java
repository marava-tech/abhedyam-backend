package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;
import com.abhedyam.service.interfaces.IBulkImportService;
import com.abhedyam.service.interfaces.ICustomerBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Bulk Import", description = "Bring in old records: Excel file, contacts or a typed list")
public class BulkImportController {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final IBulkImportService bulkImportService;
    private final ICustomerBatchService customerBatchService;

    @GetMapping("/bulk-import/template")
    @Operation(summary = "Download the Excel template", description = "Five columns: Name, Phone, Village, Owes, Due date; lang=en|te|hi names them")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam(value = "lang", defaultValue = "en") String lang) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE));
        headers.setContentDisposition(ContentDisposition.attachment().filename("Abhedyam_old_records.xlsx").build());
        return ResponseEntity.ok().headers(headers).body(bulkImportService.template(lang));
    }

    @PostMapping(value = "/owners/{ownerId}/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bring in old records from Excel", description = "Name, Phone, Village, Owes, Due date (en/te/hi headers). Same answer as customers/batch, with Excel row numbers; dryRun=true saves nothing")
    public ApiResponse<CustomerBatchResult> importData(
            @PathVariable UUID ownerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            @RequestParam(value = "label", required = false) String label) {
        return ApiResponse.success(bulkImportService.importData(ownerId, file, dryRun, label));
    }

    @PostMapping("/owners/{ownerId}/customers/batch")
    @Operation(summary = "Bring in old records", description = "Add many customers (from contacts or a typed list), each with an optional old balance. Bad rows are skipped with a code; dryRun=true saves nothing")
    public ApiResponse<CustomerBatchResult> saveBatch(
            @PathVariable UUID ownerId,
            @Valid @RequestBody CustomerBatchRequest request,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(customerBatchService.save(ownerId, request, dryRun));
    }
}
