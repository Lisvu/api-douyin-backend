package com.douyin.api.controller;

import com.douyin.api.exception.ApiException;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeNotificationProjection;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.service.RedisCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private static final Duration USER_PROFILE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final UserRelationRepository userRelationRepository;
    private final RedisCacheService redisCacheService;

    public UserController(UserRepository userRepository,
                          VideoRepository videoRepository,
                          LikeRepository likeRepository,
                          ViewRepository viewRepository,
                          FavoriteRepository favoriteRepository,
                          CommentRepository commentRepository,
                          UserRelationRepository userRelationRepository,
                          RedisCacheService redisCacheService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRelationRepository = userRelationRepository;
        this.redisCacheService = redisCacheService;
    }

    @GetMapping("/me")
    @Cacheable(value = "users", key = "#request.getAttribute('userId')")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }

        String cacheKey = userProfileCacheKey(userId);
        Optional<Map<String, Object>> cached = redisCacheService.getMap(cacheKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User session is invalid."));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", userToMap(user));
        redisCacheService.put(cacheKey, response, USER_PROFILE_TTL);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/like-notifications")
    public ResponseEntity<Map<String, Object>> getLikeNotifications(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            HttpServletRequest request) {
        User user = getCurrentUserOrThrow(request);
        Long ownerId = user.getId();
        LocalDateTime readAfter = user.getLastLikeNotificationReadAt();

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<LikeNotificationProjection> rawNotifications = cursorParts == null
                ? likeRepository.findReceivedLikeNotificationsCursor(ownerId, pageable)
                : likeRepository.findReceivedLikeNotificationsBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawNotifications.size() > safeLimit;
        List<LikeNotificationProjection> pageNotifications = hasMore
                ? rawNotifications.subList(0, safeLimit)
                : rawNotifications;

        List<Map<String, Object>> notifications = new ArrayList<>();
        for (LikeNotificationProjection item : pageNotifications) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("likeId", item.getLikeId());
            notification.put("likerUsername", item.getLikerUsername());
            notification.put("likerDisplayName", item.getLikerDisplayName());
            notification.put("videoId", item.getVideoId());
            notification.put("videoTitle", item.getVideoTitle());
            notification.put("likedAt", item.getLikedAt());
            notification.put("read", isNotificationRead(item.getLikedAt(), readAfter));
            notifications.add(notification);
        }

        long unreadCount = readAfter == null
                ? likeRepository.countReceivedLikes(ownerId)
                : likeRepository.countReceivedLikesAfter(ownerId, readAfter);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageNotifications.isEmpty()
                ? encodeCursor(pageNotifications.get(pageNotifications.size() - 1).getLikedAt(), pageNotifications.get(pageNotifications.size() - 1).getLikeId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("notifications", notifications);
        response.put("unreadCount", unreadCount);
        response.put("pagination", pagination);
        return ResponseEntity.ok(response);
    }

    private String encodeCursor(LocalDateTime timestamp, Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (timestamp + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private CursorParts decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            return new CursorParts(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private record CursorParts(LocalDateTime createdAt, Long id) {}

    @PutMapping("/me/like-notifications/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markLikeNotificationsRead(HttpServletRequest request) {
        User user = getCurrentUserOrThrow(request);
        user.setLastLikeNotificationReadAt(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Like notifications marked as read.");
        response.put("unreadCount", 0);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    @Transactional
    @CacheEvict(value = "users", key = "#request.getAttribute('userId')")
    public ResponseEntity<Map<String, Object>> deleteAccount(HttpServletRequest request) {
        User user = getCurrentUserOrThrow(request);
        Long userId = user.getId();

        List<Video> userVideos = videoRepository.findByUserId(userId);
        List<Long> userVideoIds = userVideos.stream().map(Video::getId).toList();

        if (!userVideoIds.isEmpty()) {
            likeRepository.deleteByVideoIdIn(userVideoIds);
            viewRepository.deleteByVideoIdIn(userVideoIds);
            favoriteRepository.deleteByVideoIdIn(userVideoIds);
            commentRepository.deleteByVideoIdIn(userVideoIds);
        }
        likeRepository.deleteByUserId(userId);
        viewRepository.deleteByUserId(userId);
        favoriteRepository.deleteByUserId(userId);
        commentRepository.deleteByUserId(userId);
        userRelationRepository.deleteByFollowerIdOrFollowingId(userId, userId);

        for (Video video : userVideos) {
            deleteLocalFilesForVideo(video);
            videoRepository.delete(video);
        }

        userRepository.deleteById(userId);
        redisCacheService.evictPrefix("recommendations:");
        redisCacheService.evict(userProfileCacheKey(userId));
        redisCacheService.evict("auth:user:" + user.getUsername());
        redisCacheService.evict("admin:stats");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Your account has been deleted successfully, along with all related videos and interactions.");
        return ResponseEntity.ok(response);
    }

    private User getCurrentUserOrThrow(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User session is invalid."));
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("displayName", user.getDisplayName());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("bio", user.getBio());
        userData.put("status", user.getStatus());
        userData.put("role", user.getRole());
        userData.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        userData.put("updatedAt", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        return userData;
    }

    private String userProfileCacheKey(Long userId) {
        return "user:profile:" + userId;
    }

    private boolean isNotificationRead(LocalDateTime likedAt, LocalDateTime readAfter) {
        if (readAfter == null || likedAt == null) {
            return false;
        }
        return !likedAt.isAfter(readAfter);
    }

    private void deleteLocalFilesForVideo(Video video) {
        deleteIfLocalUpload(video.getVideoUrl());
        deleteIfLocalUpload(video.getCoverUrl());
    }

    private void deleteIfLocalUpload(String url) {
        if (url != null && url.startsWith("/uploads/")) {
            File file = new File(System.getProperty("user.dir"), "public" + url);
            if (file.exists()) {
                file.delete();
            }
        }
    }
}
