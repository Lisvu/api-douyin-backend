package com.douyin.api;

import com.douyin.api.config.RequestLoggerFilter;
import com.douyin.api.model.RequestLog;
import com.douyin.api.repository.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestLoggerFilterTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    private RequestLoggerFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggerFilter(requestLogRepository);
    }

    @Test
    void requestLogEntityHasAllRequiredFields() {
        RequestLog log = new RequestLog();
        log.setMethod("POST");
        log.setUrl("/api/v1/videos");
        log.setStatusCode(201);
        log.setDurationMs(42L);
        log.setRequestBody("{\"title\":\"demo\"}");
        log.setResponseBody("{\"success\":true}");
        log.setTimestamp(LocalDateTime.now());

        assertThat(log.getMethod()).isEqualTo("POST");
        assertThat(log.getUrl()).isEqualTo("/api/v1/videos");
        assertThat(log.getStatusCode()).isEqualTo(201);
        assertThat(log.getDurationMs()).isEqualTo(42L);
        assertThat(log.getRequestBody()).isEqualTo("{\"title\":\"demo\"}");
        assertThat(log.getResponseBody()).isEqualTo("{\"success\":true}");
        assertThat(log.getTimestamp()).isInstanceOf(LocalDateTime.class);
        assertThat(log.getTimestamp()).isNotNull();
    }

    @Test
    void adminRequestLogsEndpointIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/request-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(requestLogRepository, never()).save(any());
    }

    @Test
    void adminStatsEndpointIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(requestLogRepository, never()).save(any());
    }

    @Test
    void optionsPreflightRequestIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/videos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(requestLogRepository, never()).save(any());
    }

    @Test
    void passwordFieldIsMaskedInRequestBody() {
        RequestLog log = new RequestLog();
        log.setRequestBody("{\"username\":\"alice\",\"password\":\"******\"}");

        assertThat(log.getRequestBody()).doesNotContain("123456");
        assertThat(log.getRequestBody()).contains("******");
    }

    @Test
    void unauthenticatedRequestHasNullUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContent("{\"username\":\"test\",\"password\":\"pass\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isNull();
    }

    @Test
    void nonApiPathIsNotLogged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(requestLogRepository, never()).save(any());
    }

    @Test
    void apiRequestsSavedToDatabase() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/videos/recommendations");
            request.setAttribute("userId", 1L);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
        }
        Thread.sleep(200);
        verify(requestLogRepository, times(5)).save(any());
    }
}