package com.douyin.api.repository;

import java.time.LocalDateTime;

public interface CommentItemProjection {
    Long getId();
    Long getVideoId();
    Long getUserId();
    String getUsername();
    String getDisplayName();
    String getAvatarUrl();
    String getContent();
    LocalDateTime getCreatedAt();
}
