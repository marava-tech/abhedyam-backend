package com.abhedyam.service.interfaces;

import com.abhedyam.dto.BulkImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IBulkImportService {
    default BulkImportResult importData(UUID ownerId, MultipartFile file) {
        return importData(ownerId, file, false);
    }

    BulkImportResult importData(UUID ownerId, MultipartFile file, boolean dryRun);
}
