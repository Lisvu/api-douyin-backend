package com.douyin.api.controller;

import com.douyin.api.config.JwtUtil;
import com.douyin.api.dto.AuthRequest;
import com.douyin.api.model.User;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.service.RedisCacheService;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private static final Duration AUTH_USER_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RedisCacheService redisCacheService;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil, RedisCacheService redisCacheService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.redisCacheService = redisCacheService;
    }

    // User Registration
    @PostMapping("/registrations")
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
        redisCacheService.put(authUserCacheKey(username), userToAuthCache(user), AUTH_USER_TTL);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("displayName", user.getDisplayName());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("profileBackgroundUrl", user.getProfileBackgroundUrl());
        userData.put("bio", user.getBio());
        userData.put("status", user.getStatus());
        userData.put("role", user.getRole());

        response.put("success", true);
        response.put("message", "Registration successful!");
        response.put("token", token);
        response.put("user", userData);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // User Login
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest body) {
        String username = body.getUsername();
        String password = body.getPassword();

        Map<String, Object> response = new HashMap<>();
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Username and password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<Map<String, Object>> cachedUser = redisCacheService.getMap(authUserCacheKey(username));
        if (cachedUser.isPresent()) {
            Map<String, Object> user = cachedUser.get();
            String passwordHash = (String) user.get("passwordHash");
            if (passwordHash != null && BCrypt.checkpw(password, passwordHash)) {
                Long id = ((Number) user.get("id")).longValue();
                String cachedUsername = (String) user.get("username");
                String role = (String) user.get("role");
                String token = jwtUtil.generateToken(id, cachedUsername, role);

                response.put("success", true);
                response.put("message", "Login successful!");
                response.put("token", token);
                response.put("user", cachedUserData(user));
                return ResponseEntity.ok(response);
            }
        }

        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty() || !BCrypt.checkpw(password, optionalUser.get().getPassword())) {
            response.put("success", false);
            response.put("message", "Invalid username or password.");
            return ResponseEntity.badRequest().body(response);
        }

        User user = optionalUser.get();
        redisCacheService.put(authUserCacheKey(username), userToAuthCache(user), AUTH_USER_TTL);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("displayName", user.getDisplayName());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("profileBackgroundUrl", user.getProfileBackgroundUrl());
        userData.put("bio", user.getBio());
        userData.put("status", user.getStatus());
        userData.put("role", user.getRole());

        response.put("success", true);
        response.put("message", "Login successful!");
        response.put("token", token);
        response.put("user", userData);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> userToAuthCache(User user) {
        Map<String, Object> cached = new HashMap<>();
        cached.put("id", user.getId());
        cached.put("username", user.getUsername());
        cached.put("passwordHash", user.getPassword());
        cached.put("displayName", user.getDisplayName());
        cached.put("avatarUrl", user.getAvatarUrl());
        cached.put("profileBackgroundUrl", user.getProfileBackgroundUrl());
        cached.put("bio", user.getBio());
        cached.put("status", user.getStatus());
        cached.put("role", user.getRole());
        return cached;
    }

    private Map<String, Object> cachedUserData(Map<String, Object> cached) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", ((Number) cached.get("id")).longValue());
        userData.put("username", cached.get("username"));
        userData.put("displayName", cached.get("displayName"));
        userData.put("avatarUrl", cached.get("avatarUrl"));
        userData.put("profileBackgroundUrl", cached.get("profileBackgroundUrl"));
        userData.put("bio", cached.get("bio"));
        userData.put("status", cached.get("status"));
        userData.put("role", cached.get("role"));
        return userData;
    }

    private String authUserCacheKey(String username) {
        return "auth:user:" + username;
    }
}
