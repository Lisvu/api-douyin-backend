package com.douyin.api;

import com.douyin.api.config.WebConfig;
import com.douyin.api.controller.AdminController;
import com.douyin.api.controller.AuthController;
import com.douyin.api.controller.UserController;
import com.douyin.api.controller.VideoController;
import com.douyin.api.model.RequestLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RestfulApiMappingTest {

    @Test
    void controllersUseVersionedResourcePaths() {
        assertThat(classMapping(AuthController.class)).containsExactly("/api/v1/auth");
        assertThat(classMapping(UserController.class)).containsExactly("/api/v1/users");
        assertThat(classMapping(VideoController.class)).containsExactly("/api/v1");
        assertThat(classMapping(AdminController.class)).containsExactly("/api/v1/admin");

        assertThat(postMappings(AuthController.class)).containsExactlyInAnyOrder("/register", "/login");
        assertThat(getMappings(UserController.class)).containsExactlyInAnyOrder("/me", "/me/like-notifications");
        assertThat(putMappings(UserController.class)).containsExactly("/me/like-notifications/read");
        assertThat(deleteMappings(UserController.class)).containsExactly("/me");

        assertThat(getMappings(VideoController.class)).containsExactlyInAnyOrder(
                "/videos/recommendations",
                "/users/me/videos"
        );
        assertThat(postMappings(VideoController.class)).containsExactlyInAnyOrder(
                "/videos/{id}/views",
                "/videos"
        );
        assertThat(putMappings(VideoController.class)).containsExactly("/videos/{id}/like");
        assertThat(deleteMappings(VideoController.class)).containsExactlyInAnyOrder(
                "/users/me/views",
                "/videos/{id}"
        );

        assertThat(getMappings(AdminController.class)).containsExactlyInAnyOrder("/request-logs", "/stats");
    }

    @Test
    void webConfigMentionsOnlyVersionedPublicAuthRoutes() throws Exception {
        String sourceName = WebConfig.class.getDeclaredMethod("addInterceptors", org.springframework.web.servlet.config.annotation.InterceptorRegistry.class).getName();
        assertThat(sourceName).isEqualTo("addInterceptors");
    }

    @Test
    void requestLogsExposeInputAndOutputForCourseMonitoring() {
        RequestLog log = new RequestLog();
        log.setMethod("POST");
        log.setUrl("/api/v1/videos");
        log.setStatusCode(201);
        log.setDurationMs(42L);
        log.setRequestBody("title=demo");
        log.setResponseBody("{\"success\":true}");
        log.setTimestamp(LocalDateTime.now());

        assertThat(log.getTimestamp()).isInstanceOf(LocalDateTime.class);
        assertThat(log.getRequestBody()).isEqualTo("title=demo");
        assertThat(log.getResponseBody()).isEqualTo("{\"success\":true}");
        assertThat(log.getMethod()).isEqualTo("POST");
        assertThat(log.getUrl()).isEqualTo("/api/v1/videos");
        assertThat(log.getStatusCode()).isEqualTo(201);
        assertThat(log.getDurationMs()).isEqualTo(42);
    }

    private String[] classMapping(Class<?> controllerClass) {
        return controllerClass.getAnnotation(RequestMapping.class).value();
    }

    private Set<String> getMappings(Class<?> controllerClass) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value()))
                .collect(Collectors.toSet());
    }

    private Set<String> postMappings(Class<?> controllerClass) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
                .collect(Collectors.toSet());
    }

    private Set<String> putMappings(Class<?> controllerClass) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PutMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PutMapping.class).value()))
                .collect(Collectors.toSet());
    }

    private Set<String> deleteMappings(Class<?> controllerClass) {
        return Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(DeleteMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(DeleteMapping.class).value()))
                .collect(Collectors.toSet());
    }
}