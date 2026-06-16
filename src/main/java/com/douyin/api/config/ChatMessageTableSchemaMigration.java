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
public class ChatMessageTableSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageTableSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageTableSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("chat_messages")) {
            return;
        }

        try {
            jdbcTemplate.execute("""
                    CREATE TABLE chat_messages (
                        id BIGSERIAL PRIMARY KEY,
                        from_user_id BIGINT NOT NULL,
                        to_user_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_chat_messages_pair_created ON chat_messages (from_user_id, to_user_id, created_at)"
            );
            log.info("Created chat_messages table for friend conversations.");
        } catch (Exception ex) {
            log.error("Failed to create chat_messages table.", ex);
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
