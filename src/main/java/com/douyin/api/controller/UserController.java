package com.douyin.api.controller;

import com.douyin.api.exception.ApiException;
import com.douyin.api.model.Like;
import com.douyin.api.model.Share;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.model.WatchLater;
import com.douyin.api.repository.ChatMessageRepository;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.DanmakuRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeNotificationProjection;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.ShareRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.repository.WatchLaterRepository;
import com.douyin.api.service.MediaStorageService;
import com.douyin.api.service.RedisCacheService;
import com.douyin.api.util.VideoResponseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.io.File;
import java.util.*;

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
    private final ShareRepository shareRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WatchLaterRepository watchLaterRepository;
    private final DanmakuRepository danmakuRepository;
    private final RedisCacheService redisCacheService;
    private final MediaStorageService mediaStorageService;

    public UserController(UserRepository userRepository,
                          VideoRepository videoRepository,
                          LikeRepository likeRepository,
                          ViewRepository viewRepository,
                          FavoriteRepository favoriteRepository,
                          CommentRepository commentRepository,
                          UserRelationRepository userRelationRepository,
                          ShareRepository shareRepository,
                          ChatMessageRepository chatMessageRepository,
                          WatchLaterRepository watchLaterRepository,
                          DanmakuRepository danmakuRepository,
                          RedisCacheService redisCacheService,
                          MediaStorageService mediaStorageService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRelationRepository = userRelationRepository;
        this.shareRepository = shareRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.watchLaterRepository = watchLaterRepository;
        this.danmakuRepository = danmakuRepository;
        this.redisCacheService = redisCacheService;
        this.mediaStorageService = mediaStorageService;
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
        
        List<UnifiedNotification> allNotifications = new ArrayList<>();

        // 1. Fetch Likes
        List<LikeNotificationProjection> likes = cursorParts == null
                ? likeRepository.findReceivedLikeNotificationsCursor(ownerId, pageable)
                : likeRepository.findReceivedLikeNotificationsBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        for (LikeNotificationProjection l : likes) {
            UnifiedNotification un = new UnifiedNotification();
            un.uniqueKey = "L-" + l.getLikeId();
            un.type = "like";
            un.actionId = l.getLikeId();
            un.username = l.getLikerUsername();
            un.displayName = l.getLikerDisplayName();
            un.videoId = l.getVideoId();
            un.videoTitle = l.getVideoTitle();
            un.createdAt = l.getLikedAt();
            allNotifications.add(un);
        }

        // 2. Fetch Favorites
        List<Object[]> favorites = cursorParts == null
                ? favoriteRepository.findReceivedFavoriteNotificationsCursor(ownerId, pageable)
                : favoriteRepository.findReceivedFavoriteNotificationsBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        for (Object[] f : favorites) {
            UnifiedNotification un = new UnifiedNotification();
            un.uniqueKey = "F-" + f[0];
            un.type = "favorite";
            un.actionId = (Long) f[0];
            un.username = (String) f[2];
            un.displayName = (String) f[3];
            un.videoId = (Long) f[4];
            un.videoTitle = (String) f[5];
            un.createdAt = (LocalDateTime) f[6];
            allNotifications.add(un);
        }

        // 3. Fetch Comments
        List<Object[]> comments = cursorParts == null
                ? commentRepository.findReceivedCommentNotificationsCursor(ownerId, pageable)
                : commentRepository.findReceivedCommentNotificationsBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        for (Object[] c : comments) {
            UnifiedNotification un = new UnifiedNotification();
            un.uniqueKey = "C-" + c[0];
            un.type = "comment";
            un.actionId = (Long) c[0];
            un.username = (String) c[2];
            un.displayName = (String) c[3];
            un.videoId = (Long) c[4];
            un.videoTitle = (String) c[5];
            un.content = (String) c[6];
            un.createdAt = (LocalDateTime) c[7];
            allNotifications.add(un);
        }

        // Sort combined list by createdAt DESC, then by actionId DESC
        allNotifications.sort((n1, n2) -> {
            int timeCompare = n2.createdAt.compareTo(n1.createdAt);
            if (timeCompare != 0) {
                return timeCompare;
            }
            return n2.actionId.compareTo(n1.actionId);
        });

        boolean hasMore = allNotifications.size() > safeLimit;
        List<UnifiedNotification> pageNotifications = hasMore
                ? allNotifications.subList(0, safeLimit)
                : allNotifications;

        List<Map<String, Object>> notifications = new ArrayList<>();
        for (UnifiedNotification item : pageNotifications) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("likeId", item.uniqueKey);
            notification.put("type", item.type);
            notification.put("likerUsername", item.username);
            notification.put("likerDisplayName", item.displayName);
            notification.put("videoId", item.videoId);
            notification.put("videoTitle", item.videoTitle);
            notification.put("likedAt", item.createdAt);
            notification.put("content", item.content);
            notification.put("read", isNotificationRead(item.createdAt, readAfter));
            notifications.add(notification);
        }

        long unreadLikes = readAfter == null
                ? likeRepository.countReceivedLikes(ownerId)
                : likeRepository.countReceivedLikesAfter(ownerId, readAfter);
        long unreadFavorites = readAfter == null
                ? favoriteRepository.countReceivedFavorites(ownerId)
                : favoriteRepository.countReceivedFavoritesAfter(ownerId, readAfter);
        long unreadComments = readAfter == null
                ? commentRepository.countReceivedComments(ownerId)
                : commentRepository.countReceivedCommentsAfter(ownerId, readAfter);
        long unreadCount = unreadLikes + unreadFavorites + unreadComments;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageNotifications.isEmpty()
                ? encodeCursor(pageNotifications.get(pageNotifications.size() - 1).createdAt,
                               pageNotifications.get(pageNotifications.size() - 1).actionId)
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable("id") Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", userProfileToMap(user));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/videos")
    public ResponseEntity<Map<String, Object>> getUserPublishedVideos(
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        if (!userRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Long viewerId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(buildPublishedVideosPage(id, viewerId, cursor, limit));
    }

    @GetMapping("/{id}/liked-videos")
    public ResponseEntity<Map<String, Object>> getUserLikedVideos(
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        if (!userRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Long viewerId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(buildLikedVideosPage(id, viewerId, cursor, limit));
    }

    @GetMapping("/{id}/favorited-videos")
    public ResponseEntity<Map<String, Object>> getUserFavoritedVideos(
            @PathVariable("id") Long id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        if (!userRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Long viewerId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(buildFavoritedVideosPage(id, viewerId, cursor, limit));
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
            danmakuRepository.deleteByVideoIdIn(userVideoIds);
            watchLaterRepository.deleteByVideoIdIn(userVideoIds);
            shareRepository.deleteByVideoIdIn(userVideoIds);
        }
        likeRepository.deleteByUserId(userId);
        viewRepository.deleteByUserId(userId);
        favoriteRepository.deleteByUserId(userId);
        commentRepository.deleteByUserId(userId);
        danmakuRepository.deleteByUserId(userId);
        watchLaterRepository.deleteByUserId(userId);
        shareRepository.deleteByFromUserId(userId);
        shareRepository.deleteByToUserId(userId);
        chatMessageRepository.deleteByFromUserId(userId);
        chatMessageRepository.deleteByToUserId(userId);
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

    @GetMapping("/me/liked-videos")
    public ResponseEntity<Map<String, Object>> getLikedVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }
        return ResponseEntity.ok(buildLikedVideosPage(userId, userId, cursor, limit));
    }

    @GetMapping("/me/favorited-videos")
    public ResponseEntity<Map<String, Object>> getFavoritedVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }
        return ResponseEntity.ok(buildFavoritedVideosPage(userId, userId, cursor, limit));
    }

    private Map<String, Object> buildLikedVideosPage(Long ownerId, Long viewerId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<Like> rawLikes = cursorParts == null
                ? likeRepository.findByUserIdOrderByCreatedAtDescIdDesc(ownerId, pageable)
                : likeRepository.findByUserIdBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawLikes.size() > safeLimit;
        List<Like> pageLikes = hasMore ? rawLikes.subList(0, safeLimit) : rawLikes;

        List<Map<String, Object>> videos = new ArrayList<>();
        if (!pageLikes.isEmpty()) {
            List<Long> videoIds = pageLikes.stream().map(Like::getVideoId).toList();
            List<Video> videoEntities = videoRepository.findByIdInOrderByCreatedAtDesc(videoIds);
            Map<Long, Video> videoMap = new HashMap<>();
            for (Video v : videoEntities) {
                videoMap.put(v.getId(), v);
            }
            Set<Long> likedByViewer = viewerId == null
                    ? Collections.emptySet()
                    : likeRepository.findLikedVideoIds(viewerId, videoIds);

            for (Like like : pageLikes) {
                Video v = videoMap.get(like.getVideoId());
                if (v != null) {
                    boolean liked = likedByViewer.contains(v.getId());
                    videos.add(VideoResponseMapper.toFeedItem(v, liked));
                }
            }
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageLikes.isEmpty()
                ? encodeCursor(pageLikes.get(pageLikes.size() - 1).getCreatedAt(),
                               pageLikes.get(pageLikes.size() - 1).getId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", videos);
        response.put("pagination", pagination);
        return response;
    }

    private Map<String, Object> buildPublishedVideosPage(Long ownerId, Long viewerId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<Video> rawVideos = cursorParts == null
                ? videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(ownerId, pageable)
                : videoRepository.findByUserIdBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawVideos.size() > safeLimit;
        List<Video> dbPageVideos = hasMore ? rawVideos.subList(0, safeLimit) : rawVideos;
        List<Video> pageVideos = dbPageVideos.stream()
                .filter(video -> com.douyin.api.util.LocalMediaAvailability.isPlayableUrl(video.getVideoUrl()))
                .toList();

        List<Long> videoIds = pageVideos.stream().map(Video::getId).toList();
        Set<Long> likedByViewer = videoIds.isEmpty() || viewerId == null
                ? Collections.emptySet()
                : likeRepository.findLikedVideoIds(viewerId, videoIds);

        List<Map<String, Object>> videos = new ArrayList<>();
        for (Video video : pageVideos) {
            boolean liked = likedByViewer.contains(video.getId());
            videos.add(VideoResponseMapper.toFeedItem(video, liked));
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !dbPageVideos.isEmpty()
                ? encodeCursor(dbPageVideos.get(dbPageVideos.size() - 1).getCreatedAt(),
                               dbPageVideos.get(dbPageVideos.size() - 1).getId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", videos);
        response.put("pagination", pagination);
        return response;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchUsers(
            @RequestParam("q") String keyword,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Search keyword is required.");
            return ResponseEntity.badRequest().body(response);
        }

        Pageable pageable = PageRequest.of(0, 20);
        List<User> users = userRepository.searchByKeyword(keyword.trim(), pageable);

        List<Map<String, Object>> results = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("displayName", u.getDisplayName());
            item.put("avatarUrl", u.getAvatarUrl());
            results.add(item);
        }

        response.put("success", true);
        response.put("users", results);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/shared-videos")
    public ResponseEntity<Map<String, Object>> getSharedVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<Share> rawShares = cursorParts == null
                ? shareRepository.findByToUserIdOrderByCreatedAtDesc(userId, pageable)
                : shareRepository.findByToUserIdBeforeCursor(userId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawShares.size() > safeLimit;
        List<Share> pageShares = hasMore ? rawShares.subList(0, safeLimit) : rawShares;

        List<Map<String, Object>> videos = new ArrayList<>();
        if (!pageShares.isEmpty()) {
            List<Long> videoIds = pageShares.stream().map(Share::getVideoId).toList();
            List<Video> videoEntities = videoRepository.findByIdInOrderByCreatedAtDesc(videoIds);
            Map<Long, Video> videoMap = new HashMap<>();
            for (Video v : videoEntities) {
                videoMap.put(v.getId(), v);
            }
            List<Long> senderIds = pageShares.stream().map(Share::getFromUserId).distinct().toList();
            Map<Long, String> senderNames = new HashMap<>();
            for (Long sid : senderIds) {
                userRepository.findById(sid).ifPresent(u -> senderNames.put(sid, u.getUsername()));
            }

            for (Share share : pageShares) {
                Video v = videoMap.get(share.getVideoId());
                if (v != null) {
                    Map<String, Object> item = VideoResponseMapper.toFeedItem(v, false);
                    item.put("shared_by", senderNames.getOrDefault(share.getFromUserId(), "unknown"));
                    item.put("shared_at", share.getCreatedAt().toString());
                    videos.add(item);
                }
            }
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageShares.isEmpty()
                ? encodeCursor(pageShares.get(pageShares.size() - 1).getCreatedAt(),
                               pageShares.get(pageShares.size() - 1).getId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", videos);
        response.put("pagination", pagination);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/watch-later")
    public ResponseEntity<Map<String, Object>> getWatchLaterVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<WatchLater> rawItems = cursorParts == null
                ? watchLaterRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                : watchLaterRepository.findByUserIdBeforeCursor(
                        userId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawItems.size() > safeLimit;
        List<WatchLater> pageItems = hasMore ? rawItems.subList(0, safeLimit) : rawItems;

        List<Map<String, Object>> videos = new ArrayList<>();
        if (!pageItems.isEmpty()) {
            List<Long> videoIds = pageItems.stream().map(WatchLater::getVideoId).toList();
            List<Video> videoEntities = videoRepository.findByIdInOrderByCreatedAtDesc(videoIds);
            Map<Long, Video> videoMap = new HashMap<>();
            for (Video video : videoEntities) {
                videoMap.put(video.getId(), video);
            }
            Set<Long> likedVideoIds = likeRepository.findLikedVideoIds(userId, videoIds);

            for (WatchLater item : pageItems) {
                Video video = videoMap.get(item.getVideoId());
                if (video != null && com.douyin.api.util.LocalMediaAvailability.isPlayableUrl(video.getVideoUrl())) {
                    Map<String, Object> feedItem = VideoResponseMapper.toFeedItem(video, likedVideoIds.contains(video.getId()));
                    feedItem.put("watch_later_at", item.getCreatedAt().toString());
                    videos.add(feedItem);
                }
            }
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageItems.isEmpty()
                ? encodeCursor(pageItems.get(pageItems.size() - 1).getCreatedAt(),
                               pageItems.get(pageItems.size() - 1).getId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", videos);
        response.put("pagination", pagination);
        response.put("totalCount", watchLaterRepository.countByUserId(userId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/views")
    public ResponseEntity<Map<String, Object>> getViewHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User context is missing.");
        }
        long count = viewRepository.countByUserId(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("viewCount", count);
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

    private Map<String, Object> userProfileToMap(User user) {
        Map<String, Object> profile = userToMap(user);
        Long totalLikes = videoRepository.sumLikesCountByUserId(user.getId());
        profile.put("totalLikesReceived", totalLikes == null ? 0 : totalLikes);
        profile.put("publishedVideoCount", videoRepository.countByUserId(user.getId()));
        return profile;
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
        mediaStorageService.deleteMedia(video.getVideoUrl());
        mediaStorageService.deleteMedia(video.getCoverUrl());
    }

    private Map<String, Object> buildFavoritedVideosPage(Long ownerId, Long viewerId, String cursor, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        CursorParts cursorParts = decodeCursor(cursor);
        List<com.douyin.api.model.Favorite> rawFavorites = cursorParts == null
                ? favoriteRepository.findByUserIdOrderByCreatedAtDescIdDesc(ownerId, pageable)
                : favoriteRepository.findByUserIdBeforeCursor(ownerId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawFavorites.size() > safeLimit;
        List<com.douyin.api.model.Favorite> pageFavorites = hasMore ? rawFavorites.subList(0, safeLimit) : rawFavorites;

        List<Map<String, Object>> videos = new ArrayList<>();
        if (!pageFavorites.isEmpty()) {
            List<Long> videoIds = pageFavorites.stream().map(com.douyin.api.model.Favorite::getVideoId).toList();
            List<Video> videoEntities = videoRepository.findByIdInOrderByCreatedAtDesc(videoIds);
            Map<Long, Video> videoMap = new HashMap<>();
            for (Video v : videoEntities) {
                videoMap.put(v.getId(), v);
            }
            Set<Long> likedByViewer = viewerId == null
                    ? Collections.emptySet()
                    : likeRepository.findLikedVideoIds(viewerId, videoIds);
            Set<Long> favoritedByViewer = viewerId == null
                    ? Collections.emptySet()
                    : favoriteRepository.findFavoritedVideoIds(viewerId, videoIds);
            Map<Long, Long> commentCounts = loadCommentCounts(videoIds);
            Map<Long, Long> favoriteCounts = loadFavoriteCounts(videoIds);

            for (com.douyin.api.model.Favorite fav : pageFavorites) {
                Video v = videoMap.get(fav.getVideoId());
                if (v != null) {
                    boolean liked = likedByViewer.contains(v.getId());
                    boolean favorited = favoritedByViewer.contains(v.getId());
                    videos.add(VideoResponseMapper.toFeedItem(
                            v, liked, favorited, favoriteCounts.getOrDefault(v.getId(), 0L), commentCounts.getOrDefault(v.getId(), 0L)));
                }
            }
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageFavorites.isEmpty()
                ? encodeCursor(pageFavorites.get(pageFavorites.size() - 1).getCreatedAt(),
                               pageFavorites.get(pageFavorites.size() - 1).getId())
                : null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("videos", videos);
        response.put("pagination", pagination);
        return response;
    }

    private Map<Long, Long> loadCommentCounts(Collection<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countGroupByVideoIds(videoIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<Long, Long> loadFavoriteCounts(Collection<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : favoriteRepository.countGroupByVideoIds(videoIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static class UnifiedNotification {
        String uniqueKey;
        String type;
        Long actionId;
        String username;
        String displayName;
        Long videoId;
        String videoTitle;
        LocalDateTime createdAt;
        String content;
    }
}
