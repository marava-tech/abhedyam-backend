package com.abhedyam.service;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.service.interfaces.IAccountExportService;
import com.abhedyam.service.interfaces.IAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class AccountExportService implements IAccountExportService {

    static final String SUPPORT_EMAIL = "tech.marava@gmail.com";
    static final Duration COOLDOWN = Duration.ofDays(7);
    private static final List<String> TYPES = List.of("customers", "sales", "payments");

    private final IAccountService accountService;
    private final OwnerRepository ownerRepository;
    private final OwnerSettingsRepository settingsRepository;
    private final GmailSender gmailSender;
    private final TransactionTemplate tx;
    private final Executor executor;

    public AccountExportService(IAccountService accountService, OwnerRepository ownerRepository,
                                OwnerSettingsRepository settingsRepository, GmailSender gmailSender,
                                TransactionTemplate tx, @Qualifier("virtualThreadExecutor") Executor executor) {
        this.accountService = accountService;
        this.ownerRepository = ownerRepository;
        this.settingsRepository = settingsRepository;
        this.gmailSender = gmailSender;
        this.tx = tx;
        this.executor = executor;
    }

    @Override
    public String requestExport(UUID ownerId) {
        // ponytail: read-then-write is not atomic; two taps in the same millisecond could both pass. Fine at this volume.
        String email = tx.execute(s -> {
            Owner owner = ownerRepository.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (owner.getEmail() == null || owner.getEmail().isBlank()) {
                throw new BusinessException("EMAIL_REQUIRED", "Add an email address to your profile to receive your export");
            }
            OwnerSettings settings = settingsRepository.findByOwnerId(ownerId).orElseGet(() -> {
                OwnerSettings n = new OwnerSettings();
                n.setOwnerId(ownerId);
                return n;
            });
            Instant last = settings.getLastExportRequestedAt();
            if (last != null && last.plus(COOLDOWN).isAfter(Instant.now())) {
                String when = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata")).format(last.plus(COOLDOWN));
                throw new BusinessException("RATE_LIMITED", "You can export once a week. Try again on " + when);
            }
            settings.setLastExportRequestedAt(Instant.now());
            settingsRepository.save(settings);
            return owner.getEmail();
        });
        CompletableFuture.runAsync(() -> buildAndSend(ownerId, email), executor);
        return mask(email);
    }

    @Override
    public boolean sendForDeletion(UUID ownerId, Instant deletesAt) {
        try {
            String email = ownerRepository.findById(ownerId).map(Owner::getEmail).orElse(null);
            if (email == null || email.isBlank()) return false;
            String date = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
            String when = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.of("Asia/Kolkata")).format(deletesAt);
            gmailSender.sendGmailWithAttachment(email, "Your Abhedyam data, before your account is deleted",
                    exportEmailHtml("You asked us to delete your Abhedyam account. It will be deleted on <b>" + when
                            + "</b>. Changed your mind? Open the app and tap <b>Keep my account</b> before then."),
                    "abhedyam-export-" + date + ".zip", zip(ownerId), SUPPORT_EMAIL);
            tx.executeWithoutResult(s -> settingsRepository.findByOwnerId(ownerId).ifPresent(st -> {
                st.setDeletionExportSentAt(Instant.now());
                settingsRepository.save(st);
            }));
            log.info("Deletion export emailed for owner {}", ownerId);
            return true;
        } catch (Exception e) {
            log.error("Deletion export failed for owner {}: {}", ownerId, e.getMessage());
            return false;
        }
    }

    private void buildAndSend(UUID ownerId, String email) {
        try {
            byte[] zip = zip(ownerId);
            String date = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
            gmailSender.sendGmailWithAttachment(email, "Your Abhedyam data export", exportEmailHtml(),
                    "abhedyam-export-" + date + ".zip", zip, SUPPORT_EMAIL);
            log.info("Export emailed for owner {}", ownerId);
        } catch (Exception e) {
            log.error("Export failed for owner {}: {}", ownerId, e.getMessage());
            // Give the weekly allowance back so they can retry.
            tx.executeWithoutResult(s -> settingsRepository.findByOwnerId(ownerId).ifPresent(st -> {
                st.setLastExportRequestedAt(null);
                settingsRepository.save(st);
            }));
        }
    }

    byte[] zip(UUID ownerId) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String t : TYPES) {
                z.putNextEntry(new ZipEntry(t + ".csv"));
                z.write(("﻿" + accountService.exportCsv(ownerId, t)).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    static String exportEmailHtml() {
        return exportEmailHtml(null);
    }

    /** note = an extra paragraph under the heading (used for the deletion export), or null. */
    static String exportEmailHtml(String note) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#0f172a\">"
                + "<h2 style=\"margin:0 0 16px;color:#4f46e5\">Your Abhedyam data is ready</h2>"
                + (note == null ? "" : "<p style=\"margin:0 0 12px;line-height:1.5\">" + note + "</p>")
                + "<p style=\"margin:0 0 12px;line-height:1.5\">" + (note == null ? "As you asked, your" : "Your") + " data is attached as a zip file.</p>"
                + "<p style=\"margin:0 0 6px\">Inside the zip:</p>"
                + "<ul style=\"margin:0 0 16px;padding-left:20px;line-height:1.7\">"
                + "<li><b>customers.csv</b> &ndash; names, phones, villages and what each owes</li>"
                + "<li><b>sales.csv</b> &ndash; every item sold, with dues and dates</li>"
                + "<li><b>payments.csv</b> &ndash; every payment received</li></ul>"
                + "<p style=\"margin:0 0 16px;line-height:1.5\">Open them in Excel or Google Sheets. You can request a new export once a week.</p>"
                + "<p style=\"margin:0 0 16px;line-height:1.5;color:#475569\">Didn&rsquo;t ask for this? Write to us at "
                + "<a href=\"mailto:" + SUPPORT_EMAIL + "\">" + SUPPORT_EMAIL + "</a> and we will look into it.</p>"
                + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:20px 0\">"
                + "<p style=\"margin:0;font-size:12px;color:#64748b\">Abhedyam by Marava Tech</p></div>";
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 2) return email;
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
