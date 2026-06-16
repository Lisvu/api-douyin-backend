package com.douyin.api.repository;

import com.douyin.api.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE (m.fromUserId = :user1 AND m.toUserId = :user2)
               OR (m.fromUserId = :user2 AND m.toUserId = :user1)
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChatMessage> findConversationBetweenUsers(@Param("user1") Long user1, @Param("user2") Long user2);

    void deleteByFromUserId(Long userId);
    void deleteByToUserId(Long userId);
    void deleteByFromUserIdInOrToUserIdIn(Collection<Long> fromUserIds, Collection<Long> toUserIds);
}
