package com.douyin.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ApiDouyinApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiDouyinApplication.class, args);
    }
}
