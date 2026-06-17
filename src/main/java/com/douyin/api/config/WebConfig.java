package com.douyin.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final String localUploadDir;

    public WebConfig(JwtInterceptor jwtInterceptor,
                     @Value("${app.media.local-upload-dir:public/uploads}") String localUploadDir) {
        this.jwtInterceptor = jwtInterceptor;
        this.localUploadDir = localUploadDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/registrations", "/api/v1/sessions");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // Generous CORS for simple student evaluation
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/** 由 UploadMediaController 处理（Windows 下 ResourceHandler 易出问题）
        File uploadDir = new File(localUploadDir);
        if (!uploadDir.isAbsolute()) {
            uploadDir = new File(System.getProperty("user.dir"), localUploadDir);
        }
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }
}
