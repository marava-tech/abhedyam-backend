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

    private void buildAndSend(UUID ownerId, String email) {
        try {
            byte[] zip = zip(ownerId);
            String date = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
            gmailSender.sendGmailWithAttachment(email, "Your Abhedyam data export",
                    "<p>Your data export is attached as a zip with three files: customers, sales and payments (CSV, opens in Excel or Google Sheets).</p>"
                            + "<p>If you did not ask for this, please contact tech.marava@gmail.com.</p>",
                    "abhedyam-export-" + date + ".zip", zip);
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

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 2) return email;
        return email.charAt(0) + "***" + email.substring(at - 1);
    }
}
