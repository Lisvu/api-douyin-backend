/**
 * 异常管理层 (Global Exceptions & Handlers)
 * <p>
 * 【团队开发指引】：
 * 1. 本目录下用于存放自定义业务异常（如 {@code BusinessException}、{@code UnauthorizedException}）。
 * 2. 必须包含一个统一的全局异常捕获类，例如使用 {@code @RestControllerAdvice} 注解修饰的 {@code GlobalExceptionHandler}。
 * 3. 任何层级（Controller/Service）抛出预期内的异常时，直接抛出本包中定义的异常即可，全局处理器会自动捕获并组装成标准的错误 JSON 返回给前端。
 * </p>
 */
package com.douyin.api.exception;
