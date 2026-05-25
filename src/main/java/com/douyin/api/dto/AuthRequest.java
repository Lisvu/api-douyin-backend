package com.douyin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录/注册请求参数")
public class AuthRequest {

    @Schema(description = "登录账号", example = "test_user", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "登录密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
