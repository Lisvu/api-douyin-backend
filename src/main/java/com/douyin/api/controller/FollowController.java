package com.douyin.api.controller;

import com.douyin.api.model.User;
import com.douyin.api.model.UserRelation;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
public class FollowController {

    private final UserRepository userRepository;
    private final UserRelationRepository userRelationRepository;

    public FollowController(UserRepository userRepository,
                            UserRelationRepository userRelationRepository) {
        this.userRepository = userRepository;
        this.userRelationRepository = userRelationRepository;
    }

    // ----------------------------------------------------
    // 关注某人
    // POST /api/v1/users/{id}/follow
    // ----------------------------------------------------
    @PostMapping("/{id}/follow")
    @Transactional
    public ResponseEntity<Map<String, Object>> followUser(
            @PathVariable("id") Long targetUserId,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (currentUserId.equals(targetUserId)) {
            response.put("success", false);
            response.put("message", "不能关注自己");
            return ResponseEntity.badRequest().body(response);
        }

        // 检查目标用户是否存在
        if (!userRepository.existsById(targetUserId)) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // 检查是否已关注
        Optional<UserRelation> existing =
                userRelationRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId);
        if (existing.isPresent()) {
            response.put("success", false);
            response.put("message", "已经关注该用户");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            UserRelation relation = new UserRelation();
            relation.setFollowerId(currentUserId);
            relation.setFollowingId(targetUserId);
            userRelationRepository.save(relation);

            // 判断是否互关（成为好友）
            boolean isFriend = userRelationRepository
                    .findByFollowerIdAndFollowingId(targetUserId, currentUserId)
                    .isPresent();

            response.put("success", true);
            response.put("message", isFriend ? "关注成功，你们已成为好友！" : "关注成功");
            response.put("isFriend", isFriend);
            response.put("followingCount", userRelationRepository.countByFollowerId(currentUserId));
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            response.put("success", false);
            response.put("message", "已经关注该用户");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ----------------------------------------------------
    // 取关某人
    // DELETE /api/v1/users/{id}/follow
    // ----------------------------------------------------
    @DeleteMapping("/{id}/follow")
    @Transactional
    public ResponseEntity<Map<String, Object>> unfollowUser(
            @PathVariable("id") Long targetUserId,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Optional<UserRelation> existing =
                userRelationRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId);
        if (existing.isEmpty()) {
            response.put("success", false);
            response.put("message", "你还没有关注该用户");
            return ResponseEntity.badRequest().body(response);
        }

        userRelationRepository.deleteByFollowerIdAndFollowingId(currentUserId, targetUserId);

        response.put("success", true);
        response.put("message", "已取消关注");
        response.put("followingCount", userRelationRepository.countByFollowerId(currentUserId));
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // 我关注的人列表
    // GET /api/v1/users/me/following
    // ----------------------------------------------------
    @GetMapping("/me/following")
    public ResponseEntity<Map<String, Object>> getFollowing(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<UserRelation> relations = userRelationRepository.findByFollowerId(currentUserId);
        List<Map<String, Object>> users = buildUserList(relations, true, currentUserId);

        response.put("success", true);
        response.put("users", users);
        response.put("count", users.size());
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // 我的粉丝列表
    // GET /api/v1/users/me/followers
    // ----------------------------------------------------
    @GetMapping("/me/followers")
    public ResponseEntity<Map<String, Object>> getFollowers(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<UserRelation> relations = userRelationRepository.findByFollowingId(currentUserId);
        List<Map<String, Object>> users = buildUserList(relations, false, currentUserId);

        response.put("success", true);
        response.put("users", users);
        response.put("count", users.size());
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // 我的好友列表（互关）
    // GET /api/v1/users/me/friends
    // ----------------------------------------------------
    @GetMapping("/me/friends")
    public ResponseEntity<Map<String, Object>> getFriends(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        List<UserRelation> mutualRelations = userRelationRepository.findMutualFollows(currentUserId);
        List<Map<String, Object>> friends = new ArrayList<>();

        for (UserRelation rel : mutualRelations) {
            Long friendId = rel.getFollowingId();
            userRepository.findById(friendId).ifPresent(u -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", u.getId());
                item.put("username", u.getUsername());
                item.put("displayName", u.getDisplayName());
                item.put("avatarUrl", u.getAvatarUrl());
                item.put("isFriend", true);
                friends.add(item);
            });
        }

        response.put("success", true);
        response.put("friends", friends);
        response.put("count", friends.size());
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // 查询当前用户与目标用户的关系
    // GET /api/v1/users/{id}/relation
    // ----------------------------------------------------
    @GetMapping("/{id}/relation")
    public ResponseEntity<Map<String, Object>> getRelation(
            @PathVariable("id") Long targetUserId,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        Long currentUserId = (Long) request.getAttribute("userId");

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        boolean isFollowing = userRelationRepository
                .findByFollowerIdAndFollowingId(currentUserId, targetUserId).isPresent();
        boolean isFollowedBy = userRelationRepository
                .findByFollowerIdAndFollowingId(targetUserId, currentUserId).isPresent();
        boolean isFriend = isFollowing && isFollowedBy;

        response.put("success", true);
        response.put("isFollowing", isFollowing);   // 我是否关注了对方
        response.put("isFollowedBy", isFollowedBy); // 对方是否关注了我
        response.put("isFriend", isFriend);          // 是否互关（好友）
        response.put("followingCount", userRelationRepository.countByFollowerId(currentUserId));
        response.put("followerCount", userRelationRepository.countByFollowingId(currentUserId));
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------
    // 私有工具方法
    // ----------------------------------------------------
    private List<Map<String, Object>> buildUserList(
            List<UserRelation> relations, boolean getFollowing, Long currentUserId) {
        List<Map<String, Object>> users = new ArrayList<>();

        // 获取所有互关的好友ID集合
        List<UserRelation> mutualRelations = userRelationRepository.findMutualFollows(currentUserId);
        Set<Long> mutualSet = new HashSet<>();
        for (UserRelation rel : mutualRelations) {
            mutualSet.add(rel.getFollowingId());
        }

        for (UserRelation rel : relations) {
            Long targetId = getFollowing ? rel.getFollowingId() : rel.getFollowerId();
            userRepository.findById(targetId).ifPresent(u -> {
                // 正确判断：只有在互关集合中才是好友
                boolean isFriend = mutualSet.contains(targetId);
                Map<String, Object> item = new HashMap<>();
                item.put("id", u.getId());
                item.put("username", u.getUsername());
                item.put("displayName", u.getDisplayName());
                item.put("avatarUrl", u.getAvatarUrl());
                item.put("isFriend", isFriend);
                users.add(item);
            });
        }
        return users;
    }
}