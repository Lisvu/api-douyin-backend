package com.douyin.api.controller;

import com.douyin.api.config.JwtUtil;
import com.douyin.api.dto.AuthRequest;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final LikeRepository likeRepository;
    private final ViewRepository viewRepository;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, 
                          VideoRepository videoRepository,
                          LikeRepository likeRepository, 
                          ViewRepository viewRepository,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.likeRepository = likeRepository;
        this.viewRepository = viewRepository;
        this.jwtUtil = jwtUtil;
    }

    // User Registration
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody AuthRequest body) {
        String username = body.getUsername();
        String password = body.getPassword();

        Map<String, Object> response = new HashMap<>();
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (userRepository.findByUsername(username).isPresent()) {
            response.put("success", false);
            response.put("message", "Username already taken.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());

        response.put("success", true);
        response.put("message", "Registration successful!");
        response.put("token", token);
        response.put("user", userData);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // User Login
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest body) {
        String username = body.getUsername();
        String password = body.getPassword();

        Map<String, Object> response = new HashMap<>();
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty() || !BCrypt.checkpw(password, optionalUser.get().getPassword())) {
            response.put("success", false);
            response.put("message", "Invalid username or password.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = optionalUser.get();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());

        response.put("success", true);
        response.put("message", "Login successful!");
        response.put("token", token);
        response.put("user", userData);

        return ResponseEntity.ok(response);
    }

    // Delete Account (注销账户)
    @DeleteMapping("/users/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAccount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Clean up user's videos and files
        // (Note: in standard JPA we can also write native queries, but fetching videos lets us delete actual files!)
        List<Video> userVideos = videoRepository.findAll().stream()
                .filter(v -> v.getUser().getId().equals(userId))
                .toList();

        for (Video v : userVideos) {
            deleteLocalFilesForVideo(v);
            videoRepository.delete(v);
        }

        // Delete other records referencing this user
        // (Likes created by the user, and likes received on their videos are managed via native deletions or cascade)
        // To be extremely safe, we execute explicit deletes
        // 1. Delete user views
        viewRepository.deleteByUserId(userId);
        
        // 2. Delete user likes (where user liked a video)
        List<com.douyin.api.model.Like> userLikes = likeRepository.findAll().stream()
                .filter(l -> l.getUserId().equals(userId))
                .toList();
        likeRepository.deleteAll(userLikes);

        // 3. Delete the user record
        userRepository.deleteById(userId);

        response.put("success", true);
        response.put("message", "Your account has been deleted successfully, along with all your uploaded videos.");
        return ResponseEntity.ok(response);
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
}
