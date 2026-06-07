package com.douyin.api.controller;

import com.douyin.api.exception.ApiException;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final FavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final UserRelationRepository userRelationRepository;

    public UserController(UserRepository userRepository,
                          VideoRepository videoRepository,
                          LikeRepository likeRepository,
                          ViewRepository viewRepository,
                          FavoriteRepository favoriteRepository,
                          CommentRepository commentRepository,
                          UserRelationRepository userRelationRepository) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRelationRepository = userRelationRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        User user = getCurrentUserOrThrow(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", userToMap(user));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/me")
    @Transactional
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
        userData.put("createdAt", user.getCreatedAt());
        userData.put("updatedAt", user.getUpdatedAt());
        return userData;
    }

    private void deleteLocalFilesForVideo(Video video) {
        deleteIfLocalUpload(video.getVideoUrl());
        deleteIfLocalUpload(video.getCoverUrl());
    }

    private void deleteIfLocalUpload(String url) {
        if (url != null && url.startsWith("/uploads/")) {
            File file = new File("./public" + url);
            if (file.exists()) {
                file.delete();
            }
        }
    }
}
