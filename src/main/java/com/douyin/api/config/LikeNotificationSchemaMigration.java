package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class LikeNotificationSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LikeNotificationSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public LikeNotificationSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (columnExists("users", "last_like_notification_read_at")) {
            return;
        }

        try {
            jdbcTemplate.execute(
                    "ALTER TABLE users ADD COLUMN last_like_notification_read_at TIMESTAMP"
            );
            log.info("Added users.last_like_notification_read_at column for like notifications.");
        } catch (Exception ex) {
            log.error(
                    "Failed to add users.last_like_notification_read_at. "
                            + "Run docs/migrations/001_add_last_like_notification_read_at.sql manually.",
                    ex
            );
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
