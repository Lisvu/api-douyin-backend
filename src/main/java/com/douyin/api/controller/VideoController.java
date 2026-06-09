package com.douyin.api.controller;

import com.douyin.api.model.Like;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.model.View;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.service.RedisCacheService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final RedisCacheService redisCacheService;
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
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(VideoController.class);

    public VideoController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository,
                           RedisCacheService redisCacheService) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.redisCacheService = redisCacheService;
    }

    // ----------------------------------------------------
    // RECOMMENDER SYSTEM API ENDPOINTS
    // ----------------------------------------------------

    // Get Recommended Videos (Exclude viewed, order by likes count DESC)
    @GetMapping("/videos/recommendations")
    @Tag(name = "F02/F03 推荐流", description = "组员 A：推荐列表与切换数据")
    @Operation(summary = "获取推荐视频列表（F02）", description = "排除已观看视频，按 likeCount 降序。F03 前端本地切换索引，不单独请求 next/prev。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回 videos、allViewed、totalCount"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();
        String cacheKey = recommendationsCacheKey(userId, limit);

        try {
            Optional<Map<String, Object>> cached = redisCacheService.getMap(cacheKey);
            if (cached.isPresent()) {
                return ResponseEntity.ok(cached.get());
            }

            // Fetch videos with pagination to avoid loading all at once
            List<Video> rawVideos = videoRepository.findRecommendedVideosForUser(userId, PageRequest.of(0, limit));
            long totalVideosCount = videoRepository.count();

            // Batch query: get all liked video IDs in a single DB round-trip
            List<Long> videoIds = rawVideos.stream().map(Video::getId).toList();
            Set<Long> likedVideoIds = videoIds.isEmpty()
                    ? Collections.emptySet()
                    : new HashSet<>(likeRepository.findLikedVideoIds(userId, videoIds));

            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video v : rawVideos) {
                boolean liked = likedVideoIds.contains(v.getId());
                mappedVideos.add(VideoResponseMapper.toFeedItem(v, liked));
            }

            response.put("success", true);
            response.put("videos", mappedVideos);
            response.put("allViewed", mappedVideos.isEmpty());
            response.put("totalCount", totalVideosCount);

            redisCacheService.put(cacheKey, response, RECOMMENDATIONS_TTL);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error compiling recommendations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/videos/public-samples")
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
    @PutMapping("/videos/{id}/like")
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
                // Like
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

            // Set up save directories (absolute path to avoid Tomcat temp dir issues)
            String basePath = System.getProperty("user.dir");
            File videoDir = new File(basePath, "public/uploads/videos/");
            File coverDir = new File(basePath, "public/uploads/covers/");

            if (!videoDir.exists()) videoDir.mkdirs();
            if (!coverDir.exists()) coverDir.mkdirs();

            // Save Video File
            String videoExt = getFileExtension(videoFile.getOriginalFilename(), ".mp4");
            String uniqueSuffix = System.currentTimeMillis() + "-" + Math.round(Math.random() * 1e9);
            String savedVideoName = "video-" + uniqueSuffix + videoExt;
            File targetVideoFile = new File(videoDir, savedVideoName);
            videoFile.transferTo(targetVideoFile);

            String videoUrl = "/uploads/videos/" + savedVideoName;

            // Save Cover File
            String coverUrl;
            if (coverFile != null && !coverFile.isEmpty()) {
                String coverExt = getFileExtension(coverFile.getOriginalFilename(), ".jpg");
                String savedCoverName = "cover-" + uniqueSuffix + coverExt;
                File targetCoverFile = new File(coverDir, savedCoverName);
                coverFile.transferTo(targetCoverFile);
                coverUrl = "/uploads/covers/" + savedCoverName;
            } else {
                // Generate a random gradient color placeholder if no cover uploaded
                int randomHue1 = (int) (Math.random() * 360);
                int randomHue2 = (randomHue1 + 120) % 360;
                String displayTitle = title.length() > 12 ? title.substring(0, 12) : title;
                String encodedTitle = URLEncoder.encode(displayTitle, StandardCharsets.UTF_8);
                coverUrl = String.format(
                        "https://placehold.co/600x800/g/png?text=%s&bg=linear-gradient(135deg,hsl(%d,80%%,40%%),hsl(%d,80%%,30%%))&color=fff",
                        encodedTitle, randomHue1, randomHue2
                );
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

    // View My Videos (Paginated)
    @GetMapping("/users/me/videos")
    @Cacheable(value = "userVideos", key = "#request.getAttribute('userId') + '-' + #page")
    public ResponseEntity<Map<String, Object>> getMyVideos(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "6") int limit,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        // Spring PageRequest is 0-indexed, but the request API is 1-indexed
        int adjustedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(adjustedPage, limit);

        try {
            Page<Video> videoPage = videoRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

            // Batch query: get all liked video IDs in a single DB round-trip
            List<Long> videoIds = videoPage.getContent().stream().map(Video::getId).toList();
            Set<Long> likedVideoIds = videoIds.isEmpty()
                    ? Collections.emptySet()
                    : likeRepository.findLikedVideoIds(userId, videoIds);

            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video video : videoPage.getContent()) {
                boolean liked = likedVideoIds.contains(video.getId());
                mappedVideos.add(VideoResponseMapper.toFeedItem(video, liked));
            }

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("limit", limit);
            pagination.put("total", videoPage.getTotalElements());
            pagination.put("totalPages", videoPage.getTotalPages());

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
        videoRepository.delete(video);
        redisCacheService.evictPrefix("recommendations:");
        redisCacheService.evict("admin:stats");

        // 事务外删本地文件
        deleteLocalFile(video.getVideoUrl(), "/uploads/videos/");
        deleteLocalFile(video.getCoverUrl(), "/uploads/covers/");

        response.put("success", true);
        response.put("message", "Video deleted");
        response.put("data", new HashMap<>());
        return ResponseEntity.ok(response);
    }

    private void deleteLocalFile(String url, String expectedPrefix) {
        if (url == null || !url.startsWith(expectedPrefix)) return;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(System.getProperty("user.dir"), "public" + url);
            boolean deleted = java.nio.file.Files.deleteIfExists(path);
            if (!deleted) {
                log.warn("File not found, skip delete: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", url, e);
        }
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

    private String recommendationsCacheKey(Long userId, int limit) {
        return recommendationsCachePrefix(userId) + ":limit:" + limit;
    }

    private String recommendationsCachePrefix(Long userId) {
        return "recommendations:user:" + userId;
    }
}
