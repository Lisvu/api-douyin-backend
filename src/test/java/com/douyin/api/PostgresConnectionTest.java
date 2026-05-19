package com.douyin.api;

import com.douyin.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresConnectionTest {

    @Autowired
    private Environment environment;

    @Autowired
    private UserRepository userRepository;

    @Test
    void applicationCanReadUsersFromConfiguredPostgresDatabase() {
        String url = environment.getRequiredProperty("spring.datasource.url");
        assertThat(url).startsWith("jdbc:postgresql://");
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
