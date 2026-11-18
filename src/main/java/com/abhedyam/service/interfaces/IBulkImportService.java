package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CustomerBatchResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IBulkImportService {
    /** A 5-column Excel file (Name, Phone, Village, Owes, Due date) saved like a typed list; dryRun saves nothing. */
    CustomerBatchResult importData(UUID ownerId, MultipartFile file, boolean dryRun, String label);

    /** The empty template with column names in en, te or hi. */
    byte[] template(String lang);
}
