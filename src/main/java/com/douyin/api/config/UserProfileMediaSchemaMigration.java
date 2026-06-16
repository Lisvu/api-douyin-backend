package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(9)
public class UserProfileMediaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserProfileMediaSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public UserProfileMediaSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!columnExists("users", "profile_background_url")) {
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN profile_background_url VARCHAR(255)");
                log.info("Added users.profile_background_url for custom profile wallpapers.");
            } catch (Exception ex) {
                log.warn("Could not add users.profile_background_url. Run migration manually if needed.", ex);
            }
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
