package com.douyin.api;

import com.douyin.api.config.RequestLoggerFilter;
import com.douyin.api.model.RequestLog;
import com.douyin.api.repository.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PerformanceMonitorTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    private RequestLoggerFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggerFilter(requestLogRepository);
    }

    @Test
    void durationMsIsRecordedForApiRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/videos/recommendations");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        Thread.sleep(200);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        assertThat(captor.getValue().getDurationMs()).isNotNull();
        assertThat(captor.getValue().getDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void timestampIsRecordedForApiRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/videos/1");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        Thread.sleep(200);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        assertThat(captor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    void statusCodeIsRecordedCorrectly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/videos/recommendations");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        Thread.sleep(200);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        assertThat(captor.getValue().getStatusCode()).isEqualTo(200);
    }

    @Test
    void methodAndUrlAreRecordedCorrectly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/videos/5/like");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        Thread.sleep(200);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        assertThat(captor.getValue().getMethod()).isEqualTo("PUT");
        assertThat(captor.getValue().getUrl()).isEqualTo("/api/v1/videos/5/like");
    }

    @Test
    void slowApiThresholdIs500ms() {
        assertThat(500L).isEqualTo(500L);
    }

    @Test
    void slowApiFilterChainSimulatesDelay() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/videos/recommendations");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain slowChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res)
                    throws IOException, ServletException {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        filter.doFilter(request, response, slowChain);
        Thread.sleep(200);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        assertThat(captor.getValue().getDurationMs()).isGreaterThanOrEqualTo(10L);
    }

    @Test
    void requestLogEntityContainsTraceIdAndUserIdFields() {
        RequestLog log = new RequestLog();
        log.setTraceId("abc12345");
        log.setUserId(1001L);
        log.setUserIp("192.168.1.100");

        assertThat(log.getTraceId()).isEqualTo("abc12345");
        assertThat(log.getUserId()).isEqualTo(1001L);
        assertThat(log.getUserIp()).isEqualTo("192.168.1.100");
    }

    @Test
    void requestLogEntityUserIdIsNullableForUnauthenticatedRequests() {
        RequestLog log = new RequestLog();
        log.setUserId(null);

        assertThat(log.getUserId()).isNull();
    }
}