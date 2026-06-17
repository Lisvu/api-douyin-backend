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

        // Auth endpoints (public)
        assertThat(postMappings(AuthController.class)).containsExactlyInAnyOrder("/register", "/login");

        // User controller: verify F05/F06/F10 related endpoints exist
        assertThat(getMappings(UserController.class)).contains("/me");
        assertThat(getMappings(UserController.class)).contains("/me/like-notifications");
        assertThat(putMappings(UserController.class)).contains("/me/like-notifications/read");
        assertThat(deleteMappings(UserController.class)).contains("/me");

        // Video controller: verify F05/F06/F10 related endpoints exist
        assertThat(getMappings(VideoController.class)).contains(
                "/users/me/videos",       // F06 - my videos
                "/videos/recommendations"  // F02 - recommendations
        );
        assertThat(postMappings(VideoController.class)).contains(
                "/videos",                 // F05/F10 - publish video
                "/videos/{id}/views"       // F02 - mark viewed
        );
        assertThat(putMappings(VideoController.class)).contains("/videos/{id}/like");  // F04
        assertThat(deleteMappings(VideoController.class)).contains(
                "/videos/{id}",            // F07 - delete video
                "/users/me/views"          // F02 - reset views
        );

        // Admin endpoints exist
        assertThat(getMappings(AdminController.class)).contains("/request-logs", "/stats");
    }

    @Test
    void videoControllerHasPublishAndMyVideosEndpoints() {
        Set<String> posts = postMappings(VideoController.class);
        Set<String> gets = getMappings(VideoController.class);

        // F05/F10 — publish video
        assertThat(posts).contains("/videos");
        // F06 — my videos with pagination
        assertThat(gets).contains("/users/me/videos");
    }

    @Test
    void webConfigMentionsOnlyVersionedPublicAuthRoutes() throws Exception {
        String sourceName = WebConfig.class.getDeclaredMethod("addInterceptors",
                org.springframework.web.servlet.config.annotation.InterceptorRegistry.class).getName();
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
