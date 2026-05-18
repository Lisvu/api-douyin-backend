/**
 * 切面注入层 (AOP Aspects)
 * <p>
 * 【团队开发指引】：
 * 1. 专门编写面向切面编程（AOP）的类，例如日志审计、性能监控、安全性鉴权、高频操作防刷限流等。
 * 2. 使用 {@code @Aspect} 与 {@code @Component} 注解标识。
 * 3. 命名规范：以 {@code Aspect} 结尾，例如 {@code SecurityRateLimitAspect}。
 * </p>
 */
package com.douyin.api.aspect;
