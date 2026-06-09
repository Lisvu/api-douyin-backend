package com.douyin.api.controller;

import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.RequestLogRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.service.RedisCacheService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final UserRelationRepository userRelationRepository;
    private final RequestLogRepository requestLogRepository;
    private final RedisCacheService redisCacheService;
    private static final Duration STATS_TTL = Duration.ofMinutes(1);
    private static final Duration LOGS_TTL = Duration.ofSeconds(30);

    public AdminController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository,
                           FavoriteRepository favoriteRepository,
                           CommentRepository commentRepository,
                           UserRelationRepository userRelationRepository,
                           RequestLogRepository requestLogRepository,
                           RedisCacheService redisCacheService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRelationRepository = userRelationRepository;
        this.requestLogRepository = requestLogRepository;
        this.redisCacheService = redisCacheService;
    }

    // 从数据库查询日志（支持分页和限制）
    @GetMapping("/request-logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "page", defaultValue = "1") int page) {

        Map<String, Object> response = new HashMap<>();

        // 参数校验和边界保护
        int MAX_PAGE_SIZE = 100;
        int safePage = Math.max(1, page);                             // page 最小为 1
        int safeLimit = Math.min(MAX_PAGE_SIZE, Math.max(1, limit)); // limit 限制在 1-100 之间
        String cacheKey = "admin:logs:page:" + safePage + ":limit:" + safeLimit;
        Optional<Map<String, Object>> cached = redisCacheService.getMap(cacheKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        // 分页查询，按时间倒序
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by("timestamp").descending());
        Page<com.douyin.api.model.RequestLog> dbLogs = requestLogRepository.findAllByOrderByTimestampDesc(pageable);

        List<Map<String, Object>> formattedLogs = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (com.douyin.api.model.RequestLog log : dbLogs) {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("timestamp", log.getTimestamp().format(formatter));
            logMap.put("method", log.getMethod());
            logMap.put("url", log.getUrl());
            logMap.put("statusCode", log.getStatusCode());
            logMap.put("durationMs", log.getDurationMs());
            logMap.put("requestBody", log.getRequestBody());
            logMap.put("responseBody", log.getResponseBody());
            formattedLogs.add(logMap);
        }

        response.put("success", true);
        response.put("logs", formattedLogs);
        response.put("total", dbLogs.getTotalElements());
        response.put("totalPages", dbLogs.getTotalPages());
        response.put("currentPage", safePage);
        response.put("limit", safeLimit);  // 返回实际使用的 limit，方便前端知道限制

        redisCacheService.put(cacheKey, response, LOGS_TTL);
        return ResponseEntity.ok(response);
    }

    // 统计数据（从数据库查询）
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();
        Optional<Map<String, Object>> cached = redisCacheService.getMap("admin:stats");
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        try {
            long userCount = userRepository.count();
            long videoCount = videoRepository.count();
            long likeCount = likeRepository.count();
            long viewCount = viewRepository.count();
            long favoriteCount = favoriteRepository.count();
            long commentCount = commentRepository.count();
            long relationCount = userRelationRepository.count();

            // 从数据库计算平均耗时
            Double avgDuration = requestLogRepository.getRecentAverageDurationMs();
            long totalRequests = requestLogRepository.count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("users", userCount);
            stats.put("videos", videoCount);
            stats.put("likes", likeCount);
            stats.put("views", viewCount);
            stats.put("favorites", favoriteCount);
            stats.put("comments", commentCount);
            stats.put("relations", relationCount);
            stats.put("averageResponseTimeMs", avgDuration != null ? Math.round(avgDuration * 100.0) / 100.0 : 0.0);
            stats.put("totalRequestsLogged", totalRequests);

            response.put("success", true);
            response.put("stats", stats);
            redisCacheService.put("admin:stats", response, STATS_TTL);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to retrieve statistics: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
