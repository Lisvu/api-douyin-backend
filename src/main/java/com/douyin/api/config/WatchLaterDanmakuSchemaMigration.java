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
public class WatchLaterDanmakuSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WatchLaterDanmakuSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public WatchLaterDanmakuSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        createWatchLaterTable();
        createDanmakuTable();
    }

    private void createWatchLaterTable() {
        if (tableExists("watch_later")) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE watch_later (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        video_id BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                        UNIQUE (user_id, video_id)
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_watch_later_user_created ON watch_later (user_id, created_at DESC)"
            );
            log.info("Created watch_later table.");
        } catch (Exception ex) {
            log.error("Failed to create watch_later table.", ex);
        }
    }

    private void createDanmakuTable() {
        if (tableExists("danmaku")) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE danmaku (
                        id BIGSERIAL PRIMARY KEY,
                        video_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        appear_at DOUBLE PRECISION NOT NULL,
                        color VARCHAR(16),
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_danmaku_video_appear ON danmaku (video_id, appear_at ASC)"
            );
            log.info("Created danmaku table.");
        } catch (Exception ex) {
            log.error("Failed to create danmaku table.", ex);
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
