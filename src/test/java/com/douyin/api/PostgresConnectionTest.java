package com.douyin.api;

import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresConnectionTest {

    @Autowired
    private Environment environment;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void applicationCanReadUsersFromConfiguredPostgresDatabase() {
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertThat(url).startsWith("jdbc:postgresql://");
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
        
        long count = commentRepository.count();
        System.out.println("=== COMMENT COUNT IN DATABASE: " + count + " ===");
        commentRepository.findAll().forEach(comment -> {
            System.out.println("Comment ID: " + comment.getId() + ", Content: " + comment.getContent() + ", UserId: " + comment.getUserId() + ", VideoId: " + comment.getVideoId() + ", CreatedAt: " + comment.getCreatedAt());
        });

        System.out.println("=== REQUEST LOGS AROUND COMMENT 28 ===");
        jdbcTemplate.queryForList("SELECT id, method, url, status_code, timestamp FROM request_logs WHERE timestamp >= '2026-06-16 14:25:00' AND timestamp <= '2026-06-16 14:35:00' ORDER BY id DESC")
            .forEach(u -> {
                System.out.println(u);
            });
    }
}
