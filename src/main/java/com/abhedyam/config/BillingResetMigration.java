package com.abhedyam.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-off cleanup for the billing rewrite (specs/billing.md): drops the old plan/Razorpay/referral-score columns
 * and gives owners without an access date a fresh trial. Each statement is idempotent or fails harmlessly once done.
 * ponytail: delete this class after it has run once in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingResetMigration {

    private static final List<String> STATEMENTS = List.of(
            "ALTER TABLE owners DROP COLUMN subscription",
            "ALTER TABLE owners DROP COLUMN subscription_status",
            "ALTER TABLE owners DROP COLUMN subscription_id",
            "ALTER TABLE owners DROP COLUMN valid_till",
            "ALTER TABLE owners DROP COLUMN device_hardware_id",
            "DROP TABLE IF EXISTS subscriptions",
            "DELETE FROM audits WHERE type = 'SUBSCRIPTION'",
            "ALTER TABLE referrals DROP COLUMN customer_count",
            "ALTER TABLE referrals DROP COLUMN active_days_count",
            "ALTER TABLE referrals DROP COLUMN transaction_count",
            "ALTER TABLE referrals DROP COLUMN total_volume",
            "ALTER TABLE referrals DROP COLUMN bills_shared_count",
            "ALTER TABLE referrals DROP COLUMN reward_days_granted",
            // Hibernate makes MySQL ENUM(...) columns for Java enums; new values (JOINED/PAID, BILLING) need VARCHAR
            "ALTER TABLE referrals MODIFY status VARCHAR(20) NOT NULL",
            "ALTER TABLE notifications MODIFY category VARCHAR(40)",
            "ALTER TABLE notification_muted_categories MODIFY category VARCHAR(40)",
            "UPDATE referrals SET status = 'JOINED' WHERE status NOT IN ('JOINED', 'PAID')",
            "UPDATE owners SET access_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY) WHERE access_until IS NULL");

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        for (String sql : STATEMENTS) {
            try {
                int rows = jdbc.update(sql);
                log.info("Billing reset: {} ({} rows)", sql, rows);
            } catch (Exception e) {
                log.debug("Billing reset skipped (already done): {}", sql);
            }
        }
    }
}
