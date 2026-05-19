package com.douyin.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RequestLoggerFilter implements Filter {

    public static class RequestLog {
        private final LocalDateTime timestamp = LocalDateTime.now();
        private final String method;
        private final String url;
        private final int statusCode;
        private final long durationMs;
        private final String requestBody;
        private final String responseBody;

        public RequestLog(String method, String url, int statusCode, long durationMs) {
            this(method, url, statusCode, durationMs, "", "");
        }

        public RequestLog(String method, String url, int statusCode, long durationMs, String requestBody, String responseBody) {
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.durationMs = durationMs;
            this.requestBody = requestBody;
            this.responseBody = responseBody;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getMethod() {
            return method;
        }

        public String getUrl() {
            return url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getRequestBody() {
            return requestBody;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    private static final int MAX_LOGS = 100;
    private static final int MAX_BODY_CHARS = 1000;
    private final Queue<RequestLog> logsQueue = new ConcurrentLinkedQueue<>();

    public List<RequestLog> getLogs() {
        return new ArrayList<>(logsQueue);
    }

    public double getAverageResponseTimeMs() {
        return logsQueue.stream()
                .mapToLong(RequestLog::getDurationMs)
                .average()
                .orElse(0.0);
    }

    public int getTotalRequests() {
        return logsQueue.size();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String url = httpRequest.getRequestURI();
        
        // Skip administrative polling to keep the stats/logs dashboard clean from infinite-loop request pollution
        if (url.contains("/h2-console") || url.contains("/api/v1/admin/request-logs") || url.contains("/api/v1/admin/stats") || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper((HttpServletResponse) response);
        long startTime = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = wrappedResponse.getStatus();
            String method = wrappedRequest.getMethod();

            if (url.startsWith("/api/v1")) {
                logsQueue.add(new RequestLog(
                        method,
                        url,
                        status,
                        duration,
                        bodyAsText(wrappedRequest.getContentAsByteArray()),
                        bodyAsText(wrappedResponse.getContentAsByteArray())
                ));
                while (logsQueue.size() > MAX_LOGS) {
                    logsQueue.poll();
                }
            }
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String bodyAsText(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "";
        }
        String text = new String(bodyBytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        if (text.length() <= MAX_BODY_CHARS) {
            return text;
        }
        return text.substring(0, MAX_BODY_CHARS) + "...";
    }
}
