package com.douyin.api.controller;

import com.douyin.api.model.ChatMessage;
import com.douyin.api.model.Share;
import com.douyin.api.repository.ChatMessageRepository;
import com.douyin.api.repository.ShareRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class ChatController {

    private final UserRelationRepository userRelationRepository;
    private final ShareRepository shareRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public ChatController(UserRelationRepository userRelationRepository,
                          ShareRepository shareRepository,
                          ChatMessageRepository chatMessageRepository,
                          UserRepository userRepository,
                          JdbcTemplate jdbcTemplate) {
        this.userRelationRepository = userRelationRepository;
        this.shareRepository = shareRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me/chat-history/{friendId}")
    public ResponseEntity<Map<String, Object>> getChatHistory(
            @PathVariable("friendId") Long friendId,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!isMutualFriend(currentUserId, friendId)) {
            response.put("success", false);
            response.put("message", "只能查看互关好友的对话历史。");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (!ensureChatMessagesTable(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        List<Map<String, Object>> history = new ArrayList<>();

        for (Object[] row : shareRepository.findChatHistoryBetweenUsers(currentUserId, friendId)) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "video");
            msg.put("id", row[0]);
            msg.put("fromUserId", row[1]);
            msg.put("toUserId", row[2]);
            msg.put("videoId", row[3]);
            msg.put("videoTitle", row[4]);
            msg.put("videoCoverUrl", row[5]);
            msg.put("creatorUsername", row[6]);
            msg.put("createdAt", row[7]);
            msg.put("videoUrl", row.length > 8 ? row[8] : null);
            msg.put("creatorUserId", row.length > 9 ? row[9] : null);
            history.add(msg);
        }

        for (ChatMessage textMessage : chatMessageRepository.findConversationBetweenUsers(currentUserId, friendId)) {
            history.add(chatMessageToMap(textMessage));
        }

        history.sort(Comparator
                .comparing((Map<String, Object> item) -> (LocalDateTime) item.get("createdAt"))
                .thenComparing(item -> String.valueOf(item.get("type")))
                .thenComparing(item -> ((Number) item.get("id")).longValue()));

        response.put("success", true);
        response.put("history", history);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/chat-history/{friendId}")
    public ResponseEntity<Map<String, Object>> sendChatMessage(
            @PathVariable("friendId") Long friendId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        Map<String, Object> response = new HashMap<>();

        if (currentUserId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!isMutualFriend(currentUserId, friendId)) {
            response.put("success", false);
            response.put("message", "只能给互关好友发送消息。");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (!ensureChatMessagesTable(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        String content = String.valueOf(body.getOrDefault("content", "")).trim();
        if (content.isBlank()) {
            response.put("success", false);
            response.put("message", "消息内容不能为空。");
            return ResponseEntity.badRequest().body(response);
        }
        if (content.length() > 500) {
            response.put("success", false);
            response.put("message", "消息内容不能超过 500 个字符。");
            return ResponseEntity.badRequest().body(response);
        }

        ChatMessage saved = chatMessageRepository.save(new ChatMessage(currentUserId, friendId, content));
        response.put("success", true);
        response.put("message", "消息已发送。");
        response.put("chatMessage", chatMessageToMap(saved));
        return ResponseEntity.ok(response);
    }

    private boolean isMutualFriend(Long userId, Long friendId) {
        boolean isFollowing = userRelationRepository.findByFollowerIdAndFollowingId(userId, friendId).isPresent();
        boolean isFollowed = userRelationRepository.findByFollowerIdAndFollowingId(friendId, userId).isPresent();
        return isFollowing && isFollowed;
    }

    private boolean ensureChatMessagesTable(Map<String, Object> response) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id BIGSERIAL PRIMARY KEY,
                        from_user_id BIGINT NOT NULL,
                        to_user_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_chat_messages_pair_created ON chat_messages (from_user_id, to_user_id, created_at)"
            );
            return true;
        } catch (DataAccessException ex) {
            response.put("success", false);
            response.put("message", "聊天消息表初始化失败：" + ex.getMostSpecificCause().getMessage());
            return false;
        }
    }

    private Map<String, Object> chatMessageToMap(ChatMessage message) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", "text");
        item.put("id", message.getId());
        item.put("fromUserId", message.getFromUserId());
        item.put("toUserId", message.getToUserId());
        item.put("content", message.getContent());
        item.put("createdAt", message.getCreatedAt());
        return item;
    }
}
