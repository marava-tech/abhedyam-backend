package com.abhedyam.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * fcm_details.language was created by ddl-auto as MySQL enum('EN','TE') (Hibernate 6 maps enums to native enums),
 * so saving "HI" fails with "Data truncated" (and a VARCHAR column would get CHECK (language in ('EN','TE'))).
 * ddl-auto never changes an existing column, so widen it here. Runs on every start; once the column is a VARCHAR
 * with no language check, it only reads.
 * ponytail: one-off; delete once production has started with it (like the earlier startup migrations, #45).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LanguageColumnWidening {

    private final EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void widen() {
        try {
            List<?> type = em.createNativeQuery("""
                    SELECT DATA_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fcm_details' AND COLUMN_NAME = 'language'""")
                    .getResultList();
            if (type.isEmpty()) return;
            if ("enum".equalsIgnoreCase(String.valueOf(type.get(0)))) {
                em.createNativeQuery("ALTER TABLE fcm_details MODIFY COLUMN language VARCHAR(8) NULL").executeUpdate();
                log.info("fcm_details.language widened from enum to VARCHAR(8)");
            }
            // Hibernate also adds CHECK (language in (...)) to VARCHAR enum columns; that list would block the next language.
            List<?> checks = em.createNativeQuery("""
                    SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc
                    JOIN information_schema.CHECK_CONSTRAINTS cc
                      ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                    WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'fcm_details' AND tc.CONSTRAINT_TYPE = 'CHECK'
                      AND cc.CHECK_CLAUSE LIKE '%language%'""").getResultList();
            for (Object name : checks) {
                em.createNativeQuery("ALTER TABLE fcm_details DROP CHECK `" + name + "`").executeUpdate();
                log.info("fcm_details: dropped language check {}", name);
            }
        } catch (RuntimeException e) {
            // Never block startup: until this works, phones in a new language keep their old language row.
            log.warn("Could not widen fcm_details.language: {}", e.getMessage());
        }
    }
}
