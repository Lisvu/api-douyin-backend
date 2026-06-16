package com.douyin.api.util;

import com.douyin.api.model.Video;

import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes video JSON fields for feed and like-toggle responses.
 * Canonical F04 fields: {@code liked} (boolean) and {@code likeCount} (integer).
 */
public final class VideoResponseMapper {

    private VideoResponseMapper() {
    }

    public static Map<String, Object> toFeedItem(Video video, boolean liked) {
        return toFeedItem(video, liked, false, 0, 0);
    }

    public static Map<String, Object> toFeedItem(Video video, boolean liked, long commentCount) {
        return toFeedItem(video, liked, false, 0, commentCount);
    }

    public static Map<String, Object> toFeedItem(Video video, boolean liked, boolean favorited, long favoriteCount, long commentCount) {
        int likeCount = video.getLikesCount() == null ? 0 : video.getLikesCount();

        Map<String, Object> item = new HashMap<>();
        item.put("id", video.getId());
        item.put("user_id", video.getUser().getId());
        item.put("title", video.getTitle());
        item.put("description", video.getDescription());
        item.put("video_url", video.getVideoUrl());
        item.put("cover_url", video.getCoverUrl());
        item.put("views_count", 0);
        item.put("comments_count", commentCount);
        item.put("status", "published");
        item.put("created_at", video.getCreatedAt() == null ? null : video.getCreatedAt().toString());
        item.put("creator_name", video.getUser().getUsername());

        putLikeFields(item, liked, likeCount);
        putFavoriteFields(item, favorited, favoriteCount);
        return item;
    }

    public static void putLikeFields(Map<String, Object> target, boolean liked, int likeCount) {
        target.put("liked", liked);
        target.put("likeCount", likeCount);
        // Legacy aliases kept for older clients
        target.put("is_liked", liked ? 1 : 0);
        target.put("likes_count", likeCount);
    }

    public static void putFavoriteFields(Map<String, Object> target, boolean favorited, long favoriteCount) {
        target.put("favorited", favorited);
        target.put("favoriteCount", favoriteCount);
        // Legacy aliases kept for older clients
        target.put("is_favorited", favorited ? 1 : 0);
        target.put("favorites_count", favoriteCount);
    }

}
