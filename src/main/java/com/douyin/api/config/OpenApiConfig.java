package com.douyin.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI douyinOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("api-douyin API")
                        .description("""
                                简易版抖音 REST API 文档。
                                组员 A（F02/F03）：推荐列表、观看记录、重置观看。
                                使用方式：先 POST /api/v1/auth/login 获取 token，再点击 Authorize 填入 Bearer <token>。
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
