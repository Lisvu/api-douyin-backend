package com.douyin.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

        public RequestLog(String method, String url, int statusCode, long durationMs) {
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.durationMs = durationMs;
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
    }

    private static final int MAX_LOGS = 100;
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
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String url = httpRequest.getRequestURI();
        
        // Skip administrative polling to keep the stats/logs dashboard clean from infinite-loop request pollution
        if (url.contains("/h2-console") || url.contains("/api/admin/logs") || url.contains("/api/admin/stats") || "OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = httpResponse.getStatus();
            String method = httpRequest.getMethod();

            if (url.startsWith("/api")) {
                logsQueue.add(new RequestLog(method, url, status, duration));
                while (logsQueue.size() > MAX_LOGS) {
                    logsQueue.poll();
                }
            }
        }
    }
}
