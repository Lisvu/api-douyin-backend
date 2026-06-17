package com.douyin.api.config;

import com.douyin.api.model.RequestLog;
import com.douyin.api.repository.RequestLogRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RequestLoggerFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggerFilter.class);
    private static final int MAX_BODY_CHARS = 1000;
    private static final long SLOW_THRESHOLD_MS = 500;

    private final RequestLogRepository requestLogRepository;
    public RequestLoggerFilter(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String url = httpRequest.getRequestURI();

        // 跳过 admin 监控接口、h2控制台、OPTIONS 预检、视频下载与静态上传（避免缓存大文件）
        if (url.contains("/h2-console")
                || url.contains("/api/v1/admin/request-logs")
                || url.contains("/api/v1/admin/stats")
                || url.contains("/file")
                || url.startsWith("/uploads/")
                || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper((HttpServletResponse) response);

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = wrappedResponse.getStatus();
            String method = wrappedRequest.getMethod();
            Long userId = (Long) httpRequest.getAttribute("userId");

            String reqBody = maskPassword(bodyAsText(wrappedRequest.getContentAsByteArray()));
            String resBody = maskPassword(bodyAsText(wrappedResponse.getContentAsByteArray()));

            if (url.startsWith("/api/v1")) {
                // 1. 慢接口告警
                if (duration > SLOW_THRESHOLD_MS) {
                    log.warn("Slow API detected: path={}, costMs={}ms, userId={}, traceId={}",
                            url, duration, userId, traceId);
                }

                // 2. 控制台日志（含 traceId/userId/ip）
                log.info("[{}] {} {} {} {}ms userId={} ip={}",
                        traceId, method, url, status, duration,
                        userId, getClientIp(httpRequest));

                // 3. 异步持久化到数据库（不阻塞请求响应）
                saveRequestLogAsync(method, url, status, duration, reqBody, resBody,
                        traceId, userId, getClientIp(httpRequest));
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private void saveRequestLogAsync(String method,
                                     String url,
                                     int status,
                                     long duration,
                                     String reqBody,
                                     String resBody,
                                     String traceId,
                                     Long userId,
                                     String userIp) {
        CompletableFuture.runAsync(() -> {
            try {
                com.douyin.api.model.RequestLog entity = new com.douyin.api.model.RequestLog();
                entity.setMethod(method);
                entity.setUrl(url);
                entity.setStatusCode(status);
                entity.setDurationMs(duration);
                entity.setRequestBody(reqBody);
                entity.setResponseBody(resBody);
                entity.setTimestamp(LocalDateTime.now());
                entity.setTraceId(traceId);
                entity.setUserId(userId);
                entity.setUserIp(userIp);
                requestLogRepository.save(entity);
            } catch (Exception e) {
                log.error("Failed to save request log: {}", e.getMessage());
            }
        });
    }

    private String bodyAsText(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) return "";
        String text = new String(bodyBytes, StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").trim();
        return text.length() <= MAX_BODY_CHARS
                ? text
                : text.substring(0, MAX_BODY_CHARS) + "...";
    }

    private String maskPassword(String body) {
        if (body == null || body.isEmpty()) return body;
        // 脱敏 password 字段
        body = body.replaceAll(
                "\"password\"\\s*:\\s*\"[^\"]*\"",
                "\"password\":\"******\"");
        // 脱敏 token 字段（登录响应里的 JWT）
        body = body.replaceAll(
                "\"token\"\\s*:\\s*\"[^\"]*\"",
                "\"token\":\"******\"");
        return body;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return (ip != null && !ip.isBlank())
                ? ip.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
