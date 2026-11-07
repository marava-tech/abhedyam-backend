package com.abhedyam.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, safe to re-run: products made before per-item stock tracking get a value.
 * Tracked if they have any stock, otherwise untracked (most were made on the fly from New sale and sat at 0).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockTrackingMigration implements ApplicationRunner {

    public static final String SQL = "UPDATE products p SET track_stock = CASE WHEN EXISTS ("
            + "SELECT 1 FROM inventories i WHERE i.product_id = p.id AND i.owner_id = p.owner_id AND i.stock <> 0"
            + ") THEN TRUE ELSE FALSE END WHERE p.track_stock IS NULL";

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = jdbc.update(SQL);
            if (n > 0) log.info("Stock tracking: set track_stock on {} existing products", n);
        } catch (Exception e) {
            log.warn("Stock tracking migration skipped: {}", e.getMessage());
        }
    }
}
