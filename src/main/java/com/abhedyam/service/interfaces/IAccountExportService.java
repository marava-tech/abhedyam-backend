package com.abhedyam.service.interfaces;

import java.util.UUID;

public interface IAccountExportService {
    /** Queues a zip of the owner's CSVs to their email. Returns the masked address. Once per 7 days. */
    String requestExport(UUID ownerId);
}
