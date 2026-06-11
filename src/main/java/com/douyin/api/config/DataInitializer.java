package com.douyin.api.config;

import com.douyin.api.model.User;
import com.douyin.api.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
            admin.setDisplayName("管理员");
            admin.setRole("ADMIN");
            userRepository.save(admin);
            log.info("Admin account created: {}", adminUsername);
        } else {
            // 确保已有账号的 role 是 ADMIN
            userRepository.findByUsername(adminUsername).ifPresent(u -> {
                if (!"ADMIN".equals(u.getRole())) {
                    u.setRole("ADMIN");
                    userRepository.save(u);
                    log.info("Admin role updated for: {}", adminUsername);
                }
            });
        }
    }
}