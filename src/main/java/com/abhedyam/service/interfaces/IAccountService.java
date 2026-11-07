package com.abhedyam.service.interfaces;

import java.util.UUID;

public interface IAccountService {
    /** How long after asking an owner can still change their mind. */
    java.time.Duration DELETION_GRACE = java.time.Duration.ofHours(48);
    /** The owner's data is emailed this long after they ask, without them asking for it. */
    java.time.Duration DELETION_EXPORT_AFTER = java.time.Duration.ofHours(12);

    /**
     * Ends the account without deleting its data: the owner and their customers are marked inactive, sign-in details
     * (email, Firebase id, phone, shop link) and everything that sends or receives messages are removed, so the same
     * person can sign up again with the same Gmail and add the same customers. Sales, payments and items are kept.
     * The scheduled job calls this when the grace period ends.
     */
    void disableAccount(UUID ownerId);

    /** A disabled account's data is erased this long after it was disabled. */
    java.time.Duration DISABLED_RETENTION = java.time.Duration.ofDays(90);

    /** Hard delete of the owner and everything scoped to them. The job calls this once DISABLED_RETENTION has passed. */
    void deleteAccount(UUID ownerId);

    /** Starts the grace period (asking again does not restart it). Returns when the account will be deleted. */
    java.time.Instant requestDeletion(UUID ownerId);

    /** Cancels a pending deletion. No-op when none is pending. */
    void cancelDeletion(UUID ownerId);

    /** When the account will be deleted, or empty when no deletion is pending. */
    java.util.Optional<java.time.Instant> deletionScheduledFor(UUID ownerId);

    /** CSV text for type = customers | sales | payments. */
    String exportCsv(UUID ownerId, String type);
}
