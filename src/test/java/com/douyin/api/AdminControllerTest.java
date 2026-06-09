package com.douyin.api;

import com.douyin.api.controller.AdminController;
import com.douyin.api.model.RequestLog;
import com.douyin.api.repository.*;
import com.douyin.api.service.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private ViewRepository viewRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private UserRelationRepository userRelationRepository;
    @Mock private RequestLogRepository requestLogRepository;

    @BeforeEach
    void setUp() {
        RedisCacheService noOpCache = new RedisCacheService(null, null) {
            @Override public Optional<Map<String, Object>> getMap(String key) { return Optional.empty(); }
            @Override public void put(String key, Object value, Duration ttl) {}
            @Override public void evict(String key) {}
            @Override public void evictPrefix(String prefix) {}
        };

        AdminController controller = new AdminController(
                userRepository, videoRepository, likeRepository, viewRepository,
                favoriteRepository, commentRepository, userRelationRepository,
                requestLogRepository, noOpCache
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── /stats 接口 ────────────────────────────────────────────────

    @Test
    void statsReturnsSuccessTrue() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void statsContainsUserCount() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.users").value(5));
    }

    @Test
    void statsContainsVideoCount() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.videos").value(10));
    }

    @Test
    void statsContainsLikeCount() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.likes").value(20));
    }

    @Test
    void statsContainsAverageResponseTimeMs() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.averageResponseTimeMs").value(85.5));
    }

    @Test
    void statsContainsTotalRequestsLogged() throws Exception {
        mockStats(5L, 10L, 20L, 30L, 0L, 0L, 0L, 85.5, 100L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalRequestsLogged").value(100));
    }

    @Test
    void statsAverageResponseTimeFallsBackToZeroWhenNull() throws Exception {
        mockStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, null, 0L);

        mockMvc.perform(get("/api/v1/admin/stats").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.averageResponseTimeMs").value(0.0));
    }

    // ── /request-logs 接口 ─────────────────────────────────────────

    @Test
    void requestLogsReturnsSuccessTrue() throws Exception {
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/request-logs").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void requestLogsReturnsLogsArray() throws Exception {
        RequestLog log = buildSampleLog(200, 45L);
        Page<RequestLog> page = new PageImpl<>(List.of(log));
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/request-logs").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs").isArray())
                .andExpect(jsonPath("$.logs[0].method").value("GET"))
                .andExpect(jsonPath("$.logs[0].url").value("/api/v1/videos/recommendations"))
                .andExpect(jsonPath("$.logs[0].statusCode").value(200))
                .andExpect(jsonPath("$.logs[0].durationMs").value(45));
    }

    @Test
    void requestLogsContainsTraceIdAndUserFields() throws Exception {
        RequestLog log = buildSampleLog(200, 45L);
        log.setTraceId("abc12345");
        log.setUserId(1001L);
        log.setUserIp("192.168.1.100");

        Page<RequestLog> page = new PageImpl<>(List.of(log));
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/request-logs").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[0].traceId").value("abc12345"))
                .andExpect(jsonPath("$.logs[0].userId").value(1001))
                .andExpect(jsonPath("$.logs[0].userIp").value("192.168.1.100"));
    }

    @Test
    void requestLogsRecords403ForbiddenStatus() throws Exception {
        RequestLog log = buildSampleLog(403, 12L);
        Page<RequestLog> page = new PageImpl<>(List.of(log));
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/request-logs").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[0].statusCode").value(403));
    }

    @Test
    void requestLogsRecords404NotFoundStatus() throws Exception {
        RequestLog log = buildSampleLog(404, 8L);
        Page<RequestLog> page = new PageImpl<>(List.of(log));
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/request-logs").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[0].statusCode").value(404));
    }

    @Test
    void requestLogsReturnsPaginationFields() throws Exception {
        when(requestLogRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/request-logs")
                        .param("page", "1")
                        .param("limit", "20")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    // ── 工具方法 ───────────────────────────────────────────────────

    private void mockStats(long users, long videos, long likes, long views,
                           long favorites, long comments, long relations,
                           Double avgDuration, long totalRequests) {
        when(userRepository.count()).thenReturn(users);
        when(videoRepository.count()).thenReturn(videos);
        when(likeRepository.count()).thenReturn(likes);
        when(viewRepository.count()).thenReturn(views);
        when(favoriteRepository.count()).thenReturn(favorites);
        when(commentRepository.count()).thenReturn(comments);
        when(userRelationRepository.count()).thenReturn(relations);
        when(requestLogRepository.getRecentAverageDurationMs()).thenReturn(avgDuration);
        when(requestLogRepository.count()).thenReturn(totalRequests);
    }

    private RequestLog buildSampleLog(int statusCode, long durationMs) {
        RequestLog log = new RequestLog();
        log.setMethod("GET");
        log.setUrl("/api/v1/videos/recommendations");
        log.setStatusCode(statusCode);
        log.setDurationMs(durationMs);
        log.setRequestBody(null);
        log.setResponseBody("{\"success\":true}");
        log.setTimestamp(LocalDateTime.now());
        log.setTraceId("test1234");
        log.setUserId(1L);
        log.setUserIp("127.0.0.1");
        return log;
    }
}