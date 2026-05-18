/**
 * 入参对象层 (Data Transfer Objects)
 * <p>
 * 【团队开发指引】：
 * 1. 专门用于接收前端通过 HTTP 请求（如 POST、PUT）传递进来的 JSON 请求体（RequestBody）。
 * 2. 严禁直接使用 JPA Entity 实体来接收前端的输入，必须用 DTO 进行字段隔离。
 * 3. 可以在此类中使用 {@code jakarta.validation.constraints} 注解（如 {@code @NotBlank}, {@code @Size}）进行自动化参数检验。
 * </p>
 */
package com.douyin.api.dto;
