package com.abhedyam.service.interfaces;

import java.util.UUID;

public interface IAccountExportService {
    /** Queues a zip of the owner's CSVs to their email. Returns the masked address. Once per 7 days. */
    String requestExport(UUID ownerId);

    /**
     * Emails the export now for a pending account deletion (ignores the weekly limit) and records that it was sent.
     * Returns false when there is no email address or the send failed, so the caller can try again later.
     */
    boolean sendForDeletion(UUID ownerId, java.time.Instant deletesAt);
}
