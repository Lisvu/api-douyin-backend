package com.douyin.api.controller;

import com.douyin.api.config.JwtUtil;
import com.douyin.api.dto.AuthRequest;
import com.douyin.api.model.User;
import com.douyin.api.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
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
        userData.put("displayName", user.getDisplayName());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("bio", user.getBio());
        userData.put("status", user.getStatus());

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
        userData.put("displayName", user.getDisplayName());
        userData.put("avatarUrl", user.getAvatarUrl());
        userData.put("bio", user.getBio());
        userData.put("status", user.getStatus());

        response.put("success", true);
        response.put("message", "Login successful!");
        response.put("token", token);
        response.put("user", userData);

        return ResponseEntity.ok(response);
    }
}
