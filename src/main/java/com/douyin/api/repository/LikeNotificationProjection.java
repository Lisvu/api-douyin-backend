package com.douyin.api.repository;

import java.time.LocalDateTime;

public interface LikeNotificationProjection {
    Long getLikeId();
    Long getLikerUserId();
    String getLikerUsername();
    String getLikerDisplayName();
    Long getVideoId();
    String getVideoTitle();
    LocalDateTime getLikedAt();
}
