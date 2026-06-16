package com.douyin.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class CommentTableSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommentTableSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public CommentTableSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("comments")) {
            createCommentsTable();
            return;
        }
        ensureStatusColumn();
        normalizeRootCommentParentIds();
    }

    private void createCommentsTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE comments (
                        id BIGSERIAL PRIMARY KEY,
                        video_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        parent_id BIGINT,
                        content TEXT NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'published',
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_comments_video_created ON comments (video_id, created_at DESC)"
            );
            log.info("Created comments table for video comment interactions.");
        } catch (Exception ex) {
            log.error("Failed to create comments table. Run migration manually if needed.", ex);
        }
    }

    private void ensureStatusColumn() {
        if (columnExists("comments", "status")) {
            return;
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE comments ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'published'"
            );
            log.info("Added status column to comments table.");
        } catch (Exception ex) {
            log.warn("Could not add status column to comments table. Ensure status defaults to 'published'.", ex);
        }
    }

    private void normalizeRootCommentParentIds() {
        try {
            jdbcTemplate.update("UPDATE comments SET parent_id = NULL WHERE parent_id = 0");
        } catch (Exception ex) {
            log.warn("Could not normalize root comment parent ids.", ex);
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
