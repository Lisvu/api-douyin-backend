/**
 * 出参视图层 (View Objects)
 * <p>
 * 【团队开发指引】：
 * 1. 专门用于向前端输出响应数据（ResponseBody）。
 * 2. VO 的核心作用是数据脱敏和响应格式统一，切勿直接将包含敏感信息（如密码 Hash）的 JPA Entity 实体直接返回给前端。
 * 3. 命名规范：以 {@code Vo} 结尾，例如 {@code UserProfileVo}。
 * </p>
 */
package com.douyin.api.vo;
