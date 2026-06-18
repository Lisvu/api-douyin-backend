package com.douyin.api.controller;

import com.douyin.api.model.Comment;
import com.douyin.api.model.Danmaku;
import com.douyin.api.model.Favorite;
import com.douyin.api.model.Like;
import com.douyin.api.model.Share;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.model.View;
import com.douyin.api.model.WatchLater;
import com.douyin.api.repository.CommentItemProjection;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.DanmakuRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.ShareRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.WatchLaterRepository;
import com.douyin.api.service.MediaStorageService;
import com.douyin.api.service.RedisCacheService;
import com.douyin.api.util.LocalMediaAvailability;
import com.douyin.api.util.VideoResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final ShareRepository shareRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final WatchLaterRepository watchLaterRepository;
    private final DanmakuRepository danmakuRepository;
    private final UserRelationRepository userRelationRepository;
    private final RedisCacheService redisCacheService;
    private final MediaStorageService mediaStorageService;
    private static final Duration RECOMMENDATIONS_TTL = Duration.ofSeconds(30);
    private static final int MAX_PUBLIC_SAMPLE_IMPORT_COUNT = 100;
    private static final String[] PUBLIC_SAMPLE_VIDEO_URLS = {
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
            "https://media.w3.org/2010/05/sintel/trailer.mp4",
            "https://media.w3.org/2010/05/bunny/trailer.mp4",
            "https://www.w3schools.com/html/mov_bbb.mp4",
            "https://www.w3school.com.cn/i/movie.mp4",
            "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4",
            "https://qiniu-web-assets.dcloud.net.cn/unidoc/zh/uni-app-video-courses.mp4",
            "https://sdk-release.qnsdk.com/1080_60_5000k.mp4",
            "https://sdk-release.qnsdk.com/2K_60_6048k.mp4",
            "https://sdk-release.qnsdk.com/mp4.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-5s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-10s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-15s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-20s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-30s.mp4"
    };
    private static final String[] PUBLIC_SAMPLE_TOPICS = {
            "城市夜景", "自然风光", "运动瞬间", "旅行记录", "美食探店",
            "科技演示", "学习日常", "音乐节奏", "生活片段", "创意剪辑"
    };
    public VideoController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository,
                           ShareRepository shareRepository,
                           CommentRepository commentRepository,
                           FavoriteRepository favoriteRepository,
                           WatchLaterRepository watchLaterRepository,
                           DanmakuRepository danmakuRepository,
                           UserRelationRepository userRelationRepository,
                           RedisCacheService redisCacheService,
                           MediaStorageService mediaStorageService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.shareRepository = shareRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.watchLaterRepository = watchLaterRepository;
        this.danmakuRepository = danmakuRepository;
        this.userRelationRepository = userRelationRepository;
        this.redisCacheService = redisCacheService;
        this.mediaStorageService = mediaStorageService;
    }

    // ----------------------------------------------------
    // RECOMMENDER SYSTEM API ENDPOINTS
    // ----------------------------------------------------

    @GetMapping("/featured-videos")
    @Tag(name = "F02/F03 推荐流", description = "组员 A：推荐列表与切换数据")
    @Operation(summary = "精选浏览网格", description = "按热度排序返回可播放视频，供精选页多列网格展示。")
    public ResponseEntity<Map<String, Object>> browseVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "24") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        try {
            RecommendationCursorParts cursorParts = decodeRecommendationCursor(cursor);
            Pageable pageable = PageRequest.of(0, safeLimit + 1);
            List<Video> rawVideos = cursorParts == null
                    ? videoRepository.findBrowseVideos(pageable)
                    : videoRepository.findBrowseVideosAfterCursor(
                    cursorParts.likesCount(),
                    cursorParts.id(),
                    pageable);
            boolean hasMore = rawVideos.size() > safeLimit;
            List<Video> dbPageVideos = hasMore ? rawVideos.subList(0, safeLimit) : rawVideos;
            List<Video> pageVideos = dbPageVideos.stream()
                    .filter(v -> LocalMediaAvailability.isPlayableUrl(v.getVideoUrl()))
                    .toList();

            List<Long> videoIds = pageVideos.stream().map(Video::getId).toList();
            Set<Long> likedVideoIds = videoIds.isEmpty()
                    ? Collections.emptySet()
                    : new HashSet<>(likeRepository.findLikedVideoIds(userId, videoIds));
            Set<Long> favoritedVideoIds = (userId != null && !videoIds.isEmpty())
                    ? new HashSet<>(favoriteRepository.findFavoritedVideoIds(userId, videoIds))
                    : Collections.emptySet();
            Map<Long, Long> commentCounts = loadCommentCounts(videoIds);
            Map<Long, Long> favoriteCounts = loadFavoriteCounts(videoIds);

            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video v : pageVideos) {
                boolean liked = likedVideoIds.contains(v.getId());
                boolean favorited = favoritedVideoIds.contains(v.getId());
                mappedVideos.add(VideoResponseMapper.toFeedItem(
                        v, liked, favorited, favoriteCounts.getOrDefault(v.getId(), 0L), commentCounts.getOrDefault(v.getId(), 0L)));
            }

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("limit", safeLimit);
            pagination.put("hasMore", hasMore);
            pagination.put("nextCursor", hasMore && !dbPageVideos.isEmpty()
                    ? encodeRecommendationCursor(dbPageVideos.get(dbPageVideos.size() - 1))
                    : null);

            response.put("success", true);
            response.put("videos", mappedVideos);
            response.put("pagination", pagination);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading browse videos: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/video-recommendations")
    @Tag(name = "F02/F03 推荐流", description = "组员 A：推荐列表与切换数据")
    @Operation(summary = "获取推荐视频列表（F02）", description = "排除已观看视频，按 likeCount 降序，使用游标分页返回下一批。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回 videos、allViewed、totalCount"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String cacheKey = recommendationsCacheKey(userId, cursor, safeLimit);

        try {
            Optional<Map<String, Object>> cached = redisCacheService.getMap(cacheKey);
            if (cached.isPresent()) {
                return ResponseEntity.ok(cached.get());
            }

            RecommendationCursorParts cursorParts = decodeRecommendationCursor(cursor);
            Pageable pageable = PageRequest.of(0, safeLimit + 1);
            List<Video> rawVideos = cursorParts == null
                    ? videoRepository.findRecommendedVideosForUser(userId, pageable)
                    : videoRepository.findRecommendedVideosForUserAfterCursor(
                    userId,
                    cursorParts.likesCount(),
                    cursorParts.id(),
                    pageable);
            boolean hasMore = rawVideos.size() > safeLimit;
            List<Video> dbPageVideos = hasMore ? rawVideos.subList(0, safeLimit) : rawVideos;
            List<Video> pageVideos = dbPageVideos.stream()
                    .filter(v -> LocalMediaAvailability.isPlayableUrl(v.getVideoUrl()))
                    .toList();
            long totalVideosCount = videoRepository.count();

            List<Long> videoIds = pageVideos.stream().map(Video::getId).toList();
            Set<Long> likedVideoIds = videoIds.isEmpty()
                    ? Collections.emptySet()
                    : new HashSet<>(likeRepository.findLikedVideoIds(userId, videoIds));
            Set<Long> favoritedVideoIds = (userId != null && !videoIds.isEmpty())
                    ? new HashSet<>(favoriteRepository.findFavoritedVideoIds(userId, videoIds))
                    : Collections.emptySet();
            Map<Long, Long> commentCounts = loadCommentCounts(videoIds);
            Map<Long, Long> favoriteCounts = loadFavoriteCounts(videoIds);

            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video v : pageVideos) {
                boolean liked = likedVideoIds.contains(v.getId());
                boolean favorited = favoritedVideoIds.contains(v.getId());
                mappedVideos.add(VideoResponseMapper.toFeedItem(
                        v, liked, favorited, favoriteCounts.getOrDefault(v.getId(), 0L), commentCounts.getOrDefault(v.getId(), 0L)));
            }

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("limit", safeLimit);
            pagination.put("hasMore", hasMore);
            pagination.put("nextCursor", hasMore && !dbPageVideos.isEmpty()
                    ? encodeRecommendationCursor(dbPageVideos.get(dbPageVideos.size() - 1))
                    : null);

            response.put("success", true);
            response.put("videos", mappedVideos);
            response.put("allViewed", mappedVideos.isEmpty());
            response.put("totalCount", totalVideosCount);
            response.put("pagination", pagination);

            redisCacheService.put(cacheKey, response, RECOMMENDATIONS_TTL);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error compiling recommendations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/public-sample-videos")
    @Transactional
    @CacheEvict(value = "userVideos", allEntries = true)
    public ResponseEntity<Map<String, Object>> importPublicSampleVideos(
            @RequestParam(value = "count", defaultValue = "100") int count,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (count < 1) {
            response.put("success", false);
            response.put("message", "Import count must be at least 1.");
            return ResponseEntity.badRequest().body(response);
        }

        int importCount = Math.min(count, MAX_PUBLIC_SAMPLE_IMPORT_COUNT);

        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            response.put("success", false);
            response.put("message", "User session invalid.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        int startIndex = (int) videoRepository.count();
        List<Video> videos = createPublicSampleVideos(optionalUser.get(), startIndex, importCount);
        videoRepository.saveAll(videos);
        redisCacheService.evictPrefix("recommendations:");
        redisCacheService.evict("admin:stats");

        response.put("success", true);
        response.put("message", "Public sample videos imported successfully.");
        response.put("importedCount", videos.size());
        response.put("ownerUserId", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Record Video View (Visited - prevents from being recommended again)
    @PostMapping("/videos/{id}/views")
    @Tag(name = "F02/F03 推荐流", description = "组员 A：推荐列表与切换数据")
    @Operation(summary = "标记视频已观看（F02 去重）", description = "播放成功后写入 views 表；同一用户同一视频幂等。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "视频不存在")
    })
    public ResponseEntity<Map<String, Object>> recordView(
            @Parameter(description = "视频 ID") @PathVariable("id") Long videoId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            // Insert view record if not exists
            if (!viewRepository.existsByUserIdAndVideoId(userId, videoId)) {
                Optional<Video> optionalVideo = videoRepository.findById(videoId);
                if (optionalVideo.isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Video not found.");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                View view = new View(userId, videoId);
                viewRepository.save(view);
                redisCacheService.evictPrefix(recommendationsCachePrefix(userId));
                redisCacheService.evict("admin:stats");
            }
            response.put("success", true);
            response.put("message", "Video marked as viewed.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error marking video as viewed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Reset watch history
    @DeleteMapping("/users/me/views")
    @Tag(name = "F02/F03 推荐流", description = "组员 A：推荐列表与切换数据")
    @Operation(summary = "重置当前用户观看记录（F02 调试）", description = "清空 views 表后所有视频可再次推荐。")
    @ApiResponse(responseCode = "200", description = "重置成功")
    public ResponseEntity<Map<String, Object>> resetViews(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            viewRepository.deleteByUserId(userId);
            redisCacheService.evictPrefix(recommendationsCachePrefix(userId));
            redisCacheService.evict("admin:stats");
            response.put("success", true);
            response.put("message", "Your watch history has been reset. All videos can be recommended again!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resetting watch history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Toggle video like (点赞)
    @PutMapping("/videos/{id}/likes/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable("id") Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Check video exists and get current likesCount (1 scalar query, no entity loaded)
            Integer currentLikes = videoRepository.findLikesCountById(videoId).orElse(null);
            if (currentLikes == null) {
                response.put("success", false);
                response.put("message", "Video not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 2. Check if already liked (1 query)
            Optional<Like> optionalLike = likeRepository.findByUserIdAndVideoId(userId, videoId);

            if (optionalLike.isPresent()) {
                // Unlike
                likeRepository.delete(optionalLike.get());
                videoRepository.incrementLikesCount(videoId, -1);

                response.put("success", true);
                response.put("message", "Video unliked.");
                VideoResponseMapper.putLikeFields(response, false, Math.max(0, currentLikes - 1));
            } else {
                // like
                likeRepository.save(new Like(userId, videoId));
                videoRepository.incrementLikesCount(videoId, 1);
                response.put("success", true);
                response.put("message", "Video liked!");
                VideoResponseMapper.putLikeFields(response, true, currentLikes + 1);
            }

            redisCacheService.evictPrefix(recommendationsCachePrefix(userId));
            redisCacheService.evict("admin:stats");
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException duplicateLike) {
            // Concurrent duplicate like: unique (user_id, video_id) already exists
            Integer currentLikes = videoRepository.findLikesCountById(videoId).orElse(0);
            response.put("success", true);
            response.put("message", "Video already liked.");
            VideoResponseMapper.putLikeFields(response, true, currentLikes);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling like: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Toggle video favorite (收藏)
    @PutMapping("/videos/{id}/favorites/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleFavorite(@PathVariable("id") Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Check video exists
            if (!videoRepository.existsById(videoId)) {
                response.put("success", false);
                response.put("message", "Video not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 2. Check if already favorited
            Optional<Favorite> optionalFav = favoriteRepository.findByUserIdAndVideoId(userId, videoId);

            boolean favorited;
            long count;
            if (optionalFav.isPresent()) {
                // Unfavorite
                favoriteRepository.delete(optionalFav.get());
                favorited = false;
                response.put("message", "Video unfavorited.");
            } else {
                // Favorite
                Favorite fav = new Favorite();
                fav.setUserId(userId);
                fav.setVideoId(videoId);
                favoriteRepository.save(fav);
                favorited = true;
                response.put("message", "Video favorited!");
            }

            count = favoriteRepository.countByVideoId(videoId);
            response.put("success", true);
            VideoResponseMapper.putFavoriteFields(response, favorited, count);

            redisCacheService.evictPrefix(recommendationsCachePrefix(userId));
            redisCacheService.evict("admin:stats");
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException duplicateFav) {
            long count = favoriteRepository.countByVideoId(videoId);
            response.put("success", true);
            response.put("message", "Video already favorited.");
            VideoResponseMapper.putFavoriteFields(response, true, count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling favorite: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ----------------------------------------------------
    // SEARCH — 搜索视频（标题、描述、标签、评论内容）和用户
    // ----------------------------------------------------

    @GetMapping("/videos")
    @Operation(summary = "搜索视频")
    public ResponseEntity<Map<String, Object>> searchVideos(
            @RequestParam("q") String keyword,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "搜索关键词不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        String q = keyword.trim();
        Long currentUserId = (Long) request.getAttribute("userId");
        Pageable pageable = PageRequest.of(0, 20);

        // 1. 按标题/描述（含标签）搜视频
        List<Video> titleMatched = videoRepository.searchByKeyword(q, pageable);

        // 2. 按评论内容找视频ID，再查视频
        List<Long> commentVideoIds = commentRepository.findVideoIdsByCommentKeyword(q, pageable);
        List<Video> commentMatched = commentVideoIds.isEmpty()
                ? List.of()
                : videoRepository.findByIdInOrderByCreatedAtDesc(commentVideoIds);

        // 3. 合并去重，标题匹配优先
        Map<Long, Video> videoMap = new LinkedHashMap<>();
        for (Video v : titleMatched) videoMap.put(v.getId(), v);
        for (Video v : commentMatched) videoMap.putIfAbsent(v.getId(), v);

        // 4. 搜用户
        List<User> users = userRepository.searchByKeyword(q, pageable);

        // 5. 组装视频响应（批量查点赞状态）
        List<Long> videoIds = new ArrayList<>(videoMap.keySet());
        Set<Long> likedVideoIds = (currentUserId != null && !videoIds.isEmpty())
                ? new HashSet<>(likeRepository.findLikedVideoIds(currentUserId, videoIds))
                : Collections.emptySet();
        Set<Long> favoritedVideoIds = (currentUserId != null && !videoIds.isEmpty())
                ? new HashSet<>(favoriteRepository.findFavoritedVideoIds(currentUserId, videoIds))
                : Collections.emptySet();
        Map<Long, Long> commentCounts = loadCommentCounts(videoIds);
        Map<Long, Long> favoriteCounts = loadFavoriteCounts(videoIds);

        List<Map<String, Object>> videoResults = new ArrayList<>();
        for (Video v : videoMap.values()) {
            if (!LocalMediaAvailability.isPlayableUrl(v.getVideoUrl())) {
                continue;
            }
            boolean liked = likedVideoIds.contains(v.getId());
            boolean favorited = favoritedVideoIds.contains(v.getId());
            videoResults.add(VideoResponseMapper.toFeedItem(
                    v, liked, favorited, favoriteCounts.getOrDefault(v.getId(), 0L), commentCounts.getOrDefault(v.getId(), 0L)));
        }

        // 6. 组装用户响应
        List<Map<String, Object>> userResults = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("displayName", u.getDisplayName());
            item.put("avatarUrl", u.getAvatarUrl());
            userResults.add(item);
        }

        response.put("success", true);
        response.put("keyword", q);
        response.put("videos", videoResults);
        response.put("users", userResults);
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // VIDEO COMMENTS
    // ----------------------------------------------------

    @GetMapping("/videos/{id}/comments")
    public ResponseEntity<Map<String, Object>> getVideoComments(
            @PathVariable("id") Long videoId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        Map<String, Object> response = new HashMap<>();

        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);
        CursorParts cursorParts = decodeCursor(cursor);
        List<CommentItemProjection> rawComments = cursorParts == null
                ? commentRepository.findTopLevelByVideoId(videoId, pageable)
                : commentRepository.findTopLevelByVideoIdBeforeCursor(
                        videoId, cursorParts.createdAt(), cursorParts.id(), pageable);
        boolean hasMore = rawComments.size() > safeLimit;
        List<CommentItemProjection> pageComments = hasMore
                ? rawComments.subList(0, safeLimit)
                : rawComments;

        List<Long> parentIds = pageComments.stream().map(CommentItemProjection::getId).toList();
        Map<Long, List<Map<String, Object>>> repliesByParentId = new HashMap<>();
        if (!parentIds.isEmpty()) {
            Set<Long> rootIdSet = new HashSet<>(parentIds);
            List<CommentItemProjection> replies = commentRepository.findRepliesByRootParentIds(parentIds);
            Map<Long, Long> replyRootIds = new HashMap<>();
            for (CommentItemProjection reply : replies) {
                if (rootIdSet.contains(reply.getParentId())) {
                    replyRootIds.put(reply.getId(), reply.getParentId());
                }
            }
            for (CommentItemProjection reply : replies) {
                Long rootParentId = replyRootIds.getOrDefault(reply.getParentId(), reply.getParentId());
                repliesByParentId
                        .computeIfAbsent(rootParentId, ignored -> new ArrayList<>())
                        .add(commentProjectionToMap(reply));
            }
        }

        List<Map<String, Object>> comments = new ArrayList<>();
        for (CommentItemProjection item : pageComments) {
            Map<String, Object> comment = commentProjectionToMap(item);
            comment.put("replies", repliesByParentId.getOrDefault(item.getId(), List.of()));
            comments.add(comment);
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("limit", safeLimit);
        pagination.put("hasMore", hasMore);
        pagination.put("nextCursor", hasMore && !pageComments.isEmpty()
                ? encodeCursor(pageComments.get(pageComments.size() - 1).getCreatedAt(),
                               pageComments.get(pageComments.size() - 1).getId())
                : null);

        response.put("success", true);
        response.put("comments", comments);
        response.put("totalCount", commentRepository.countByVideoId(videoId));
        response.put("pagination", pagination);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/videos/{id}/comments")
    @Transactional
    public ResponseEntity<Map<String, Object>> createVideoComment(
            @PathVariable("id") Long videoId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        Object contentObj = body == null ? null : body.get("content");
        String content = contentObj == null ? "" : contentObj.toString().trim();
        if (content.isEmpty()) {
            response.put("success", false);
            response.put("message", "Comment content is required.");
            return ResponseEntity.badRequest().body(response);
        }
        if (content.length() > 500) {
            response.put("success", false);
            response.put("message", "Comment content must be at most 500 characters.");
            return ResponseEntity.badRequest().body(response);
        }

        Long parentId = parseOptionalLong(body == null ? null : body.get("parentId"));
        Comment parentComment = null;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId).orElse(null);
            if (parentComment == null || !Objects.equals(parentComment.getVideoId(), videoId)) {
                response.put("success", false);
                response.put("message", "Parent comment not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        }

        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setParentId(parentComment == null ? null : parentComment.getId());
        comment.setContent(content);
        commentRepository.save(comment);
        redisCacheService.evictPrefix("recommendations:");

        User author = userRepository.findById(userId).orElse(null);
        Map<String, Object> commentData = new HashMap<>();
        commentData.put("id", comment.getId());
        commentData.put("videoId", comment.getVideoId());
        commentData.put("userId", comment.getUserId());
        commentData.put("parentId", comment.getParentId());
        commentData.put("username", author != null ? author.getUsername() : "");
        commentData.put("displayName", author != null ? author.getDisplayName() : "");
        commentData.put("avatarUrl", author != null ? author.getAvatarUrl() : null);
        commentData.put("content", comment.getContent());
        commentData.put("createdAt", comment.getCreatedAt());

        response.put("success", true);
        response.put("message", "Comment posted.");
        response.put("comment", commentData);
        response.put("commentsCount", commentRepository.countByVideoId(videoId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/videos/{id}/danmaku")
    public ResponseEntity<Map<String, Object>> getVideoDanmaku(@PathVariable("id") Long videoId) {
        Map<String, Object> response = new HashMap<>();
        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Danmaku danmaku : danmakuRepository.findByVideoIdOrderByAppearAtAscIdAsc(videoId)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", danmaku.getId());
            item.put("videoId", danmaku.getVideoId());
            item.put("userId", danmaku.getUserId());
            item.put("content", danmaku.getContent());
            item.put("appearAt", danmaku.getAppearAt());
            item.put("color", danmaku.getColor());
            item.put("createdAt", danmaku.getCreatedAt());
            items.add(item);
        }

        response.put("success", true);
        response.put("danmaku", items);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/videos/{id}/danmaku")
    @Transactional
    public ResponseEntity<Map<String, Object>> createVideoDanmaku(
            @PathVariable("id") Long videoId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        Object contentObj = body == null ? null : body.get("content");
        String content = contentObj == null ? "" : contentObj.toString().trim();
        if (content.isEmpty()) {
            response.put("success", false);
            response.put("message", "Danmaku content is required.");
            return ResponseEntity.badRequest().body(response);
        }
        if (content.length() > 100) {
            response.put("success", false);
            response.put("message", "Danmaku content must be at most 100 characters.");
            return ResponseEntity.badRequest().body(response);
        }

        double appearAt = 0;
        Object appearAtObj = body == null ? null : body.get("appearAt");
        if (appearAtObj instanceof Number number) {
            appearAt = Math.max(0, number.doubleValue());
        } else if (appearAtObj != null) {
            try {
                appearAt = Math.max(0, Double.parseDouble(appearAtObj.toString()));
            } catch (NumberFormatException ignored) {
                appearAt = 0;
            }
        }

        String color = null;
        Object colorObj = body == null ? null : body.get("color");
        if (colorObj != null) {
            color = colorObj.toString().trim();
            if (color.length() > 16) {
                color = color.substring(0, 16);
            }
        }

        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(videoId);
        danmaku.setUserId(userId);
        danmaku.setContent(content);
        danmaku.setAppearAt(appearAt);
        danmaku.setColor(color);
        danmakuRepository.save(danmaku);

        User author = userRepository.findById(userId).orElse(null);
        Map<String, Object> danmakuData = new HashMap<>();
        danmakuData.put("id", danmaku.getId());
        danmakuData.put("videoId", danmaku.getVideoId());
        danmakuData.put("userId", danmaku.getUserId());
        danmakuData.put("username", author != null ? author.getUsername() : "");
        danmakuData.put("content", danmaku.getContent());
        danmakuData.put("appearAt", danmaku.getAppearAt());
        danmakuData.put("color", danmaku.getColor());
        danmakuData.put("createdAt", danmaku.getCreatedAt());

        response.put("success", true);
        response.put("message", "Danmaku posted.");
        response.put("danmaku", danmakuData);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/videos/{id}/watch-later-items/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> addWatchLater(
            @PathVariable("id") Long videoId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        if (watchLaterRepository.existsByUserIdAndVideoId(userId, videoId)) {
            response.put("success", true);
            response.put("message", "Already in watch later.");
            response.put("inWatchLater", true);
            response.put("watchLaterCount", watchLaterRepository.countByUserId(userId));
            return ResponseEntity.ok(response);
        }

        WatchLater watchLater = new WatchLater();
        watchLater.setUserId(userId);
        watchLater.setVideoId(videoId);
        watchLaterRepository.save(watchLater);

        response.put("success", true);
        response.put("message", "Added to watch later.");
        response.put("inWatchLater", true);
        response.put("watchLaterCount", watchLaterRepository.countByUserId(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/videos/{id}/watch-later-items/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeWatchLater(
            @PathVariable("id") Long videoId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        watchLaterRepository.deleteByUserIdAndVideoId(userId, videoId);
        response.put("success", true);
        response.put("message", "Removed from watch later.");
        response.put("inWatchLater", false);
        response.put("watchLaterCount", watchLaterRepository.countByUserId(userId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/videos/{id}/watch-later-items/me")
    public ResponseEntity<Map<String, Object>> getWatchLaterStatus(
            @PathVariable("id") Long videoId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (userId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        response.put("success", true);
        response.put("inWatchLater", watchLaterRepository.existsByUserIdAndVideoId(userId, videoId));
        response.put("watchLaterCount", watchLaterRepository.countByUserId(userId));
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // MY VIDEOS MANAGEMENT API ENDPOINTS
    // ----------------------------------------------------

    // Publish Video (accepts video file, optional cover file, title and description)
    @PostMapping("/videos")
    @CacheEvict(value = "userVideos", allEntries = true)
    public ResponseEntity<Map<String, Object>> publishVideo(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("video") MultipartFile videoFile,
            @RequestParam(value = "cover", required = false) MultipartFile coverFile,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (title == null || title.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Video title is required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (videoFile == null || videoFile.isEmpty()) {
            response.put("success", false);
            response.put("message", "Video file is required.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            if (optionalUser.isEmpty()) {
                response.put("success", false);
                response.put("message", "User session invalid.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            User user = optionalUser.get();

            String uniqueSuffix = System.currentTimeMillis() + "-" + Math.round(Math.random() * 1e9);
            String videoExt = MediaStorageService.getFileExtension(videoFile.getOriginalFilename(), ".mp4");
            String savedVideoName = "video-" + uniqueSuffix + videoExt;
            String videoUrl = mediaStorageService.storeUpload(videoFile, "videos", savedVideoName);

            String coverUrl;
            if (coverFile != null && !coverFile.isEmpty()) {
                String coverExt = MediaStorageService.getFileExtension(coverFile.getOriginalFilename(), ".jpg");
                String savedCoverName = "cover-" + uniqueSuffix + coverExt;
                coverUrl = mediaStorageService.storeUpload(coverFile, "covers", savedCoverName);
            } else {
                coverUrl = mediaStorageService.storeGeneratedCover(title, "covers", "cover-" + uniqueSuffix + ".jpg");
            }

            // Create Video Entity
            Video video = new Video();
            video.setUser(user);
            video.setTitle(title);
            video.setDescription(description == null ? "" : description);
            video.setVideoUrl(videoUrl);
            video.setCoverUrl(coverUrl);
            video.setLikesCount(0);

            videoRepository.save(video);
            redisCacheService.evictPrefix("recommendations:");
            redisCacheService.evict("admin:stats");

            Map<String, Object> videoData = VideoResponseMapper.toFeedItem(video, false);

            response.put("success", true);
            response.put("message", "Video published successfully!");
            response.put("data", videoData);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "File upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // View My Videos (Cursor Paginated)
    @GetMapping("/users/me/videos")
    @Cacheable(value = "userVideos", key = "#request.getAttribute('userId') + '-' + (#cursor ?: 'first') + '-' + #limit")
    public ResponseEntity<Map<String, Object>> getMyVideos(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "8") int limit,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit + 1);

        try {
            CursorParts cursorParts = decodeCursor(cursor);
            List<Video> rawVideos = cursorParts == null
                    ? videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                    : videoRepository.findByUserIdBeforeCursor(userId, cursorParts.createdAt(), cursorParts.id(), pageable);
            boolean hasMore = rawVideos.size() > safeLimit;
            List<Video> dbPageVideos = hasMore ? rawVideos.subList(0, safeLimit) : rawVideos;
            List<Video> pageVideos = dbPageVideos.stream()
                    .filter(v -> LocalMediaAvailability.isPlayableUrl(v.getVideoUrl()))
                    .toList();

            // Batch query: get all liked video IDs in a single DB round-trip
            List<Long> videoIds = pageVideos.stream().map(Video::getId).toList();
            Set<Long> likedVideoIds = videoIds.isEmpty()
                    ? Collections.emptySet()
                    : likeRepository.findLikedVideoIds(userId, videoIds);
            Set<Long> favoritedVideoIds = (userId != null && !videoIds.isEmpty())
                    ? favoriteRepository.findFavoritedVideoIds(userId, videoIds)
                    : Collections.emptySet();
            Map<Long, Long> commentCounts = loadCommentCounts(videoIds);
            Map<Long, Long> favoriteCounts = loadFavoriteCounts(videoIds);

            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video video : pageVideos) {
                boolean liked = likedVideoIds.contains(video.getId());
                boolean favorited = favoritedVideoIds.contains(video.getId());
                mappedVideos.add(VideoResponseMapper.toFeedItem(
                        video, liked, favorited, favoriteCounts.getOrDefault(video.getId(), 0L), commentCounts.getOrDefault(video.getId(), 0L)));
            }

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("limit", safeLimit);
            pagination.put("hasMore", hasMore);
            pagination.put("nextCursor", hasMore && !dbPageVideos.isEmpty()
                    ? encodeCursor(dbPageVideos.get(dbPageVideos.size() - 1).getCreatedAt(), dbPageVideos.get(dbPageVideos.size() - 1).getId())
                    : null);

            response.put("success", true);
            response.put("videos", mappedVideos);
            response.put("pagination", pagination);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading your videos: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/videos/{id}/file")
    @Operation(summary = "获取视频文件")
    public ResponseEntity<StreamingResponseBody> downloadVideo(@PathVariable("id") Long videoId) throws IOException {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        String videoUrl = video.getVideoUrl();
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video file not available");
        }

        HttpHeaders headers = buildDownloadHeaders(video);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers);

        if (videoUrl.startsWith("/uploads/")) {
            File file = new File(System.getProperty("user.dir"), "public" + videoUrl);
            if (!file.exists() || !file.isFile()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video file not found");
            }
            StreamingResponseBody stream = outputStream -> {
                try (InputStream input = Files.newInputStream(file.toPath())) {
                    input.transferTo(outputStream);
                }
            };
            return builder.contentLength(file.length()).body(stream);
        }

        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
            HttpURLConnection connection = openRemoteConnection(videoUrl);
            long contentLength = connection.getContentLengthLong();
            StreamingResponseBody stream = outputStream -> {
                try (InputStream input = connection.getInputStream()) {
                    input.transferTo(outputStream);
                } finally {
                    connection.disconnect();
                }
            };
            if (contentLength > 0) {
                return builder.contentLength(contentLength).body(stream);
            }
            return builder.body(stream);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported video URL");
    }


    // Delete My Video (owner permission checking)
    @DeleteMapping("/videos/{id}")
    @Transactional
    @CacheEvict(value = "userVideos", allEntries = true)
    public ResponseEntity<Map<String, Object>> deleteVideo(
            @PathVariable("id") Long videoId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new LinkedHashMap<>();

        // 查视频是否存在
        Optional<Video> optionalVideo = videoRepository.findById(videoId);
        if (optionalVideo.isEmpty()) {
            response.put("success", false);
            response.put("message", "Video not found");
            response.put("data", new HashMap<>());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        Video video = optionalVideo.get();

        // 权限校验，只能删自己的视频
        if (!video.getUser().getId().equals(userId)) {
            response.put("success", false);
            response.put("message", "Forbidden: you can only delete your own videos");
            response.put("data", new HashMap<>());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // 事务内删关联记录 + 主记录
        likeRepository.deleteByVideoId(videoId);
        viewRepository.deleteByVideoId(videoId);
        commentRepository.deleteByVideoIdIn(List.of(videoId));
        danmakuRepository.deleteByVideoIdIn(List.of(videoId));
        watchLaterRepository.deleteByVideoIdIn(List.of(videoId));
        videoRepository.delete(video);
        redisCacheService.evictPrefix("recommendations:");
        redisCacheService.evict("admin:stats");

        // 事务外删本地文件
        mediaStorageService.deleteMedia(video.getVideoUrl());
        mediaStorageService.deleteMedia(video.getCoverUrl());

        response.put("success", true);
        response.put("message", "Video deleted");
        response.put("data", new HashMap<>());
        return ResponseEntity.ok(response);
    }

    // Batch delete own videos
    @PostMapping("/video-deletion-jobs")
    @Transactional
    @CacheEvict(value = "userVideos", allEntries = true)
    public ResponseEntity<Map<String, Object>> batchDeleteVideos(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("videoIds");
        if (rawIds == null || rawIds.isEmpty()) {
            response.put("success", false);
            response.put("message", "videoIds is required and must be non-empty");
            response.put("data", new HashMap<>());
            return ResponseEntity.badRequest().body(response);
        }

        List<Long> videoIds = rawIds.stream().map(Integer::longValue).toList();

        List<Video> videos = videoRepository.findAllById(videoIds);
        if (videos.size() != videoIds.size()) {
            response.put("success", false);
            response.put("message", "部分视频ID无效");
            response.put("data", new HashMap<>());
            return ResponseEntity.badRequest().body(response);
        }

        for (Video video : videos) {
            if (!video.getUser().getId().equals(userId)) {
                response.put("success", false);
                response.put("message", "视频 " + video.getId() + " 不属于你，无法删除");
                response.put("data", new HashMap<>());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

        likeRepository.deleteByVideoIdIn(videoIds);
        viewRepository.deleteByVideoIdIn(videoIds);
        favoriteRepository.deleteByVideoIdIn(videoIds);
        commentRepository.deleteByVideoIdIn(videoIds);
        danmakuRepository.deleteByVideoIdIn(videoIds);
        watchLaterRepository.deleteByVideoIdIn(videoIds);
        shareRepository.deleteByVideoIdIn(videoIds);
        videoRepository.deleteAll(videos);

        redisCacheService.evictPrefix("recommendations:");
        redisCacheService.evict("admin:stats");

        for (Video video : videos) {
            mediaStorageService.deleteMedia(video.getVideoUrl());
            mediaStorageService.deleteMedia(video.getCoverUrl());
        }

        response.put("success", true);
        response.put("message", "成功删除 " + videos.size() + " 个视频");
        response.put("data", new HashMap<>());
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // SHARE VIDEO — Forward a video to another user
    // ----------------------------------------------------

    @PostMapping("/videos/{id}/shares")
    @Transactional
    public ResponseEntity<Map<String, Object>> shareVideo(
            @PathVariable("id") Long videoId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long fromUserId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (fromUserId == null) {
            response.put("success", false);
            response.put("message", "Not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Object toUserIdObj = body.get("toUserId");
        if (toUserIdObj == null) {
            response.put("success", false);
            response.put("message", "toUserId is required.");
            return ResponseEntity.badRequest().body(response);
        }
        Long toUserId = toUserIdObj instanceof Integer
                ? Long.valueOf((Integer) toUserIdObj)
                : Long.valueOf(toUserIdObj.toString());

        if (toUserId.equals(fromUserId)) {
            response.put("success", false);
            response.put("message", "Cannot share a video to yourself.");
            return ResponseEntity.badRequest().body(response);
        }

        // Check video exists
        if (!videoRepository.existsById(videoId)) {
            response.put("success", false);
            response.put("message", "Video not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Check target user exists
        if (!userRepository.existsById(toUserId)) {
            response.put("success", false);
            response.put("message", "Target user not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Verify mutual friends
        boolean isFollowing = userRelationRepository.findByFollowerIdAndFollowingId(fromUserId, toUserId).isPresent();
        boolean isFollowed = userRelationRepository.findByFollowerIdAndFollowingId(toUserId, fromUserId).isPresent();
        if (!isFollowing || !isFollowed) {
            response.put("success", false);
            response.put("message", "只能分享视频给互相关注的好友。");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Check duplicate
        if (shareRepository.existsByFromUserIdAndToUserIdAndVideoId(fromUserId, toUserId, videoId)) {
            response.put("success", false);
            response.put("message", "You have already shared this video with that user.");
            return ResponseEntity.badRequest().body(response);
        }

        shareRepository.save(new Share(fromUserId, toUserId, videoId));

        response.put("success", true);
        response.put("message", "Video shared successfully!");
        response.put("share", Map.of("fromUserId", fromUserId, "toUserId", toUserId, "videoId", videoId));
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // PRIVATE HELPERS
    // ----------------------------------------------------

    private String buildDownloadFilename(Video video) {
        String title = video.getTitle() == null ? "video" : video.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (title.isEmpty()) {
            title = "video";
        }
        if (title.length() > 60) {
            title = title.substring(0, 60);
        }
        String path = video.getVideoUrl() == null ? "" : video.getVideoUrl().split("\\?")[0];
        return title + "_" + video.getId() + getFileExtension(path, ".mp4");
    }

    private HttpHeaders buildDownloadHeaders(Video video) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(buildDownloadFilename(video), StandardCharsets.UTF_8)
                .build());
        return headers;
    }

    private HttpURLConnection openRemoteConnection(String videoUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(videoUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(180000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "video/mp4,video/*,*/*");
        connection.connect();
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch remote video");
        }
        return connection;
    }

    private String getFileExtension(String originalFilename, String fallback) {
        if (originalFilename == null) return fallback;
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex == -1) return fallback;
        return originalFilename.substring(lastDotIndex).toLowerCase();
    }

    private List<Video> createPublicSampleVideos(User creator, int startIndex, int count) {
        List<Video> videos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int sequence = startIndex + i + 1;
            String topic = PUBLIC_SAMPLE_TOPICS[sequence % PUBLIC_SAMPLE_TOPICS.length];

            Video video = new Video();
            video.setUser(creator);
            video.setTitle(String.format("公开视频素材 %03d - %s", sequence, topic));
            video.setDescription(String.format(
                    "由当前登录用户导入的公开视频素材。#%s #公开视频 #推荐系统 #样本%03d",
                    topic, sequence));
            video.setVideoUrl(PUBLIC_SAMPLE_VIDEO_URLS[sequence % PUBLIC_SAMPLE_VIDEO_URLS.length]);
            video.setCoverUrl("https://picsum.photos/seed/douyin-public-video-" + sequence + "/600/800");
            video.setLikesCount((sequence * 37) % 900 + 20);
            videos.add(video);
        }
        return videos;
    }

    private String encodeCursor(LocalDateTime timestamp, Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (timestamp + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private String encodeRecommendationCursor(Video video) {
        int likesCount = video.getLikesCount() == null ? 0 : video.getLikesCount();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (likesCount + "|" + video.getId()).getBytes(StandardCharsets.UTF_8));
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

    private RecommendationCursorParts decodeRecommendationCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid recommendation cursor format");
            }
            return new RecommendationCursorParts(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    private record RecommendationCursorParts(int likesCount, Long id) {}

    private String recommendationsCacheKey(Long userId, String cursor, int limit) {
        return recommendationsCachePrefix(userId)
                + ":cursor:" + (cursor == null || cursor.isBlank() ? "first" : cursor)
                + ":limit:" + limit;
    }

    private String recommendationsCachePrefix(Long userId) {
        return "recommendations:user:" + userId;
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

    private Map<String, Object> commentProjectionToMap(CommentItemProjection item) {
        Map<String, Object> comment = new HashMap<>();
        comment.put("id", item.getId());
        comment.put("videoId", item.getVideoId());
        comment.put("userId", item.getUserId());
        comment.put("parentId", item.getParentId());
        comment.put("username", item.getUsername());
        comment.put("displayName", item.getDisplayName());
        comment.put("avatarUrl", item.getAvatarUrl());
        comment.put("content", item.getContent());
        comment.put("createdAt", item.getCreatedAt());
        return comment;
    }

    private Long parseOptionalLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
