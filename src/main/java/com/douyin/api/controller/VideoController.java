package com.douyin.api.controller;

import com.douyin.api.model.Like;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.model.View;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.*;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;

    public VideoController(UserRepository userRepository,
                           VideoRepository videoRepository,
                           LikeRepository likeRepository,
                           ViewRepository viewRepository) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
    }

    // ----------------------------------------------------
    // RECOMMENDER SYSTEM API ENDPOINTS
    // ----------------------------------------------------

    // Get Recommended Videos (Exclude viewed, order by likes count DESC)
    @GetMapping("/videos/recommend")
    public ResponseEntity<Map<String, Object>> getRecommendations(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            // Get all recommended videos for user
            List<Video> rawVideos = videoRepository.findRecommendedVideosForUser(userId);
            long totalVideosCount = videoRepository.count();

            // Map each video to include extra fields: creator_name and is_liked
            List<Map<String, Object>> mappedVideos = new ArrayList<>();
            for (Video v : rawVideos) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", v.getId());
                map.put("user_id", v.getUser().getId());
                map.put("title", v.getTitle());
                map.put("description", v.getDescription());
                map.put("video_url", v.getVideoUrl());
                map.put("cover_url", v.getCoverUrl());
                map.put("likes_count", v.getLikesCount());
                map.put("created_at", v.getCreatedAt());
                map.put("creator_name", v.getUser().getUsername());

                // Check if user has liked this video
                boolean isLiked = likeRepository.existsByUserIdAndVideoId(userId, v.getId());
                map.put("is_liked", isLiked ? 1 : 0);

                mappedVideos.add(map);
            }

            response.put("success", true);
            response.put("videos", mappedVideos);
            response.put("allViewed", mappedVideos.isEmpty());
            response.put("totalCount", totalVideosCount);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error compiling recommendations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Record Video View (Visited - prevents from being recommended again)
    @PostMapping("/videos/{id}/view")
    public ResponseEntity<Map<String, Object>> recordView(@PathVariable("id") Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            // Insert view record if not exists
            if (!viewRepository.existsByUserIdAndVideoId(userId, videoId)) {
                View view = new View(userId, videoId);
                viewRepository.save(view);
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
    @PostMapping("/videos/reset-views")
    public ResponseEntity<Map<String, Object>> resetViews(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            viewRepository.deleteByUserId(userId);
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
    @PostMapping("/videos/{id}/like")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable("id") Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Video> optionalVideo = videoRepository.findById(videoId);
            if (optionalVideo.isEmpty()) {
                response.put("success", false);
                response.put("message", "Video not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Video video = optionalVideo.get();
            Optional<Like> optionalLike = likeRepository.findByUserIdAndVideoId(userId, videoId);

            if (optionalLike.isPresent()) {
                // Already liked -> UNLIKE
                likeRepository.delete(optionalLike.get());
                video.setLikesCount(Math.max(0, video.getLikesCount() - 1));
                videoRepository.save(video);

                response.put("success", true);
                response.put("liked", false);
                response.put("likes_count", video.getLikesCount());
                response.put("message", "Video unliked.");
            } else {
                // Not liked -> LIKE
                Like like = new Like(userId, videoId);
                likeRepository.save(like);

                video.setLikesCount(video.getLikesCount() + 1);
                videoRepository.save(video);

                response.put("success", true);
                response.put("liked", true);
                response.put("likes_count", video.getLikesCount());
                response.put("message", "Video liked!");
            }

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
    @PostMapping("/videos/publish")
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

            // Set up save directories
            File videoDir = new File("./public/uploads/videos/");
            File coverDir = new File("./public/uploads/covers/");

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

            response.put("success", true);
            response.put("message", "Video published successfully!");
            response.put("video", video);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "File upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // View My Videos (Paginated)
    @GetMapping("/videos/my")
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

            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("limit", limit);
            pagination.put("total", videoPage.getTotalElements());
            pagination.put("totalPages", videoPage.getTotalPages());

            response.put("success", true);
            response.put("videos", videoPage.getContent());
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
    public ResponseEntity<Map<String, Object>> deleteVideo(@PathVariable("id") Long videoId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Video> optionalVideo = videoRepository.findById(videoId);
            if (optionalVideo.isEmpty()) {
                response.put("success", false);
                response.put("message", "Video not found.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Video video = optionalVideo.get();

            // STRICT OWNER CHECKING
            if (!video.getUser().getId().equals(userId)) {
                response.put("success", false);
                response.put("message", "Permission denied. You can only delete your own uploaded videos.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Delete physical local files
            deleteLocalFilesForVideo(video);

            // Delete DB record
            videoRepository.delete(video);

            response.put("success", true);
            response.put("message", "Video deleted successfully!");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting video: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private void deleteLocalFilesForVideo(Video video) {
        if (video.getVideoUrl() != null && video.getVideoUrl().startsWith("/uploads/videos/")) {
            File file = new File("." + video.getVideoUrl());
            if (file.exists()) {
                file.delete();
            }
        }
        if (video.getCoverUrl() != null && video.getCoverUrl().startsWith("/uploads/covers/")) {
            File file = new File("." + video.getCoverUrl());
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private String getFileExtension(String originalFilename, String fallback) {
        if (originalFilename == null) return fallback;
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex == -1) return fallback;
        return originalFilename.substring(lastDotIndex).toLowerCase();
    }
}
