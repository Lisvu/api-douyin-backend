package com.douyin.api.controller;

import com.douyin.api.config.RequestLoggerFilter;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final RequestLoggerFilter requestLoggerFilter;

    public AdminController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository,
                           RequestLoggerFilter requestLoggerFilter) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.requestLoggerFilter = requestLoggerFilter;
    }

    // Retrieve real-time request logs
    @GetMapping("/request-logs")
    public ResponseEntity<Map<String, Object>> getLogs() {
        Map<String, Object> response = new HashMap<>();
        List<RequestLoggerFilter.RequestLog> rawLogs = requestLoggerFilter.getLogs();

        List<Map<String, Object>> formattedLogs = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Format and reverse to show most recent logs first
        for (int i = rawLogs.size() - 1; i >= 0; i--) {
            RequestLoggerFilter.RequestLog log = rawLogs.get(i);
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
        return ResponseEntity.ok(response);
    }

    // Retrieve system dashboard statistics
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            long userCount = userRepository.count();
            long videoCount = videoRepository.count();
            long likeCount = likeRepository.count();
            long viewCount = viewRepository.count();

            double avgDuration = requestLoggerFilter.getAverageResponseTimeMs();
            int totalRequests = requestLoggerFilter.getLogs().size();

            Map<String, Object> stats = new HashMap<>();
            stats.put("users", userCount);
            stats.put("videos", videoCount);
            stats.put("likes", likeCount);
            stats.put("views", viewCount);
            stats.put("averageResponseTimeMs", Math.round(avgDuration * 100.0) / 100.0); // rounded to 2 decimals
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
