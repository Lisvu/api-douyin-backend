package com.douyin.api.controller;

import com.douyin.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final RequestLogRepository requestLogRepository;  // 新增

    public AdminController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository,
                           RequestLogRepository requestLogRepository) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.requestLogRepository = requestLogRepository;
    }

    // 从数据库查询日志（支持分页和限制）
    @GetMapping("/request-logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "page", defaultValue = "1") int page) {

        Map<String, Object> response = new HashMap<>();

        // 分页查询，按时间倒序
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("timestamp").descending());
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
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    // 统计数据（从数据库查询）
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            long userCount = userRepository.count();
            long videoCount = videoRepository.count();
            long likeCount = likeRepository.count();
            long viewCount = viewRepository.count();

            // 从数据库计算平均耗时
            Double avgDuration = requestLogRepository.getAverageDurationMs();
            long totalRequests = requestLogRepository.count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("users", userCount);
            stats.put("videos", videoCount);
            stats.put("likes", likeCount);
            stats.put("views", viewCount);
            stats.put("averageResponseTimeMs", avgDuration != null ? Math.round(avgDuration * 100.0) / 100.0 : 0.0);
            stats.put("totalRequestsLogged", totalRequests);

            response.put("success", true);
            response.put("stats", stats);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to retrieve statistics: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}