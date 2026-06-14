package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ShareTableSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShareTableSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public ShareTableSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("shares")) {
            return;
        }

        try {
            jdbcTemplate.execute("""
                    CREATE TABLE shares (
                        id BIGSERIAL PRIMARY KEY,
                        from_user_id BIGINT NOT NULL,
                        to_user_id BIGINT NOT NULL,
                        video_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_shares_to_user_created ON shares (to_user_id, created_at DESC)"
            );
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_shares_from_user ON shares (from_user_id)"
            );
            log.info("Created shares table for video forwarding feature.");
        } catch (Exception ex) {
            log.error(
                    "Failed to create shares table. "
                            + "Run docs/migrations/002_create_shares_table.sql manually.",
                    ex
            );
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
