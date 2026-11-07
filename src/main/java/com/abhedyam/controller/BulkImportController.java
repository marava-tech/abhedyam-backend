package com.abhedyam.controller;

import com.abhedyam.dto.ApiResponse;
import com.abhedyam.dto.BulkImportResult;
import com.abhedyam.service.interfaces.IBulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Bulk Import", description = "Excel bulk import APIs for owners")
public class BulkImportController {

    private static final String TEMPLATE_FILENAME = "Abhedyam_Customer_Import.xlsx";
    private static final String TEMPLATE_PATH = "templates/" + TEMPLATE_FILENAME;
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final IBulkImportService bulkImportService;

    @GetMapping("/bulk-import/template")
    @Operation(summary = "Download bulk import template", description = "Download the sample Excel template for owner data import")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE));
        headers.setContentDisposition(ContentDisposition.attachment().filename(TEMPLATE_FILENAME).build());
        headers.setContentLength(resource.contentLength());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    @PostMapping(value = "/owners/{ownerId}/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import owner data from Excel", description = "Import customers, sales, and payments from a filled Excel workbook, or dry-run validate if dryRun=true")
    public ApiResponse<BulkImportResult> importData(
            @PathVariable UUID ownerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(bulkImportService.importData(ownerId, file, dryRun));
    }
}
