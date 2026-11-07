package com.abhedyam.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, safe to re-run: the map pin became optional (a village is a label, the pin belongs to Location tools).
 * Hibernate's ddl-auto=update never relaxes NOT NULL, so do it here, then clear the fake 0,0 pins that were only
 * written to hold a village, and drop the "No Village" placeholder text.
 * ponytail: plain startup SQL, no Flyway; move to Flyway if migrations become regular.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VillagePinMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("ALTER TABLE location_details MODIFY latitude DECIMAL(10,7) NULL");
            jdbc.execute("ALTER TABLE location_details MODIFY longitude DECIMAL(10,7) NULL");
            int pins = jdbc.update("UPDATE location_details SET latitude = NULL, longitude = NULL WHERE latitude = 0 AND longitude = 0");
            int placeholders = jdbc.update("UPDATE location_details SET village = NULL WHERE LOWER(TRIM(village)) = 'no village'");
            if (pins + placeholders > 0) log.info("Village/pin split: cleared {} fake 0,0 pins and {} 'No Village' labels", pins, placeholders);
        } catch (Exception e) {
            log.warn("Village/pin migration skipped: {}", e.getMessage()); // e.g. H2 in tests uses other DDL syntax
        }
    }
}
