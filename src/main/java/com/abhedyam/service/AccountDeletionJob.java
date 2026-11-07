package com.abhedyam.service;

import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.service.interfaces.IAccountExportService;
import com.abhedyam.service.interfaces.IAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Runs the timed steps of an account deletion: email the data at 12 hours, disable at 48 hours, erase the data 90 days after that. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionJob {

    private final OwnerSettingsRepository settings;
    private final OwnerRepository owners;
    private final IAccountService accounts;
    private final IAccountExportService exports;

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        run(Instant.now());
    }

    void run(Instant now) {
        for (OwnerSettings s : settings.findByDeletionRequestedAtNotNull()) {
            try {
                Instant asked = s.getDeletionRequestedAt();
                Instant deletesAt = asked.plus(IAccountService.DELETION_GRACE);
                boolean exportDue = s.getDeletionExportSentAt() == null && !now.isBefore(asked.plus(IAccountService.DELETION_EXPORT_AFTER));
                // Not sent (no email, or Gmail failed) is retried every 15 minutes until the deletion itself.
                if (exportDue) exports.sendForDeletion(s.getOwnerId(), deletesAt);
                if (!now.isBefore(deletesAt)) accounts.disableAccount(s.getOwnerId());
            } catch (Exception e) {
                log.error("Pending deletion failed for owner {}: {}", s.getOwnerId(), e.getMessage(), e);
            }
        }
        for (Owner o : owners.findByIsActiveFalseAndDeletedAtBefore(now.minus(IAccountService.DISABLED_RETENTION))) {
            try {
                accounts.deleteAccount(o.getId());
            } catch (Exception e) {
                log.error("Purge of disabled owner {} failed: {}", o.getId(), e.getMessage(), e);
            }
        }
    }
}
