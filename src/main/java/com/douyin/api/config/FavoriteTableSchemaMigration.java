package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class FavoriteTableSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FavoriteTableSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public FavoriteTableSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("favorites")) {
            createFavoritesTable();
            return;
        }

        ensureIndexes();
    }

    private void createFavoritesTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE favorites (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        video_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        CONSTRAINT uk_favorites_user_video UNIQUE (user_id, video_id)
                    )
                    """);
            ensureIndexes();
            log.info("Created favorites table for video collection feature.");
        } catch (Exception ex) {
            log.error("Failed to create favorites table. Run migration manually if needed.", ex);
        }
    }

    private void ensureIndexes() {
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_favorites_user_created ON favorites (user_id, created_at DESC, id DESC)"
            );
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_favorites_video_id ON favorites (video_id)"
            );
        } catch (Exception ex) {
            log.warn("Could not ensure favorites indexes. Collection queries may be slower.", ex);
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
