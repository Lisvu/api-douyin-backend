package com.douyin.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String PREFIX = "douyin:";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> getMap(String key) {
        try {
            String value = redisTemplate.opsForValue().get(namespaced(key));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, MAP_TYPE));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, bypass cache read for key={}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read Redis cache key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String namespacedKey = namespaced(key);
            redisTemplate.opsForValue().set(namespacedKey, objectMapper.writeValueAsString(value), ttl);
            log.debug("Redis cache written key={} ttlSeconds={}", namespacedKey, ttl.toSeconds());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, bypass cache write for key={}", key);
        } catch (Exception e) {
            log.warn("Failed to write Redis cache key={}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            String namespacedKey = namespaced(key);
            redisTemplate.delete(namespacedKey);
            log.debug("Redis cache evicted key={}", namespacedKey);
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache key={}: {}", key, e.getMessage());
        }
    }

    public void evictPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(namespaced(prefix) + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Redis cache evicted prefix={} count={}", namespaced(prefix), keys.size());
            }
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache prefix={}: {}", prefix, e.getMessage());
        }
    }

    private String namespaced(String key) {
        return PREFIX + key;
    }
}
