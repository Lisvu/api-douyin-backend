package com.douyin.api.repository;

import com.douyin.api.model.Share;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {

    // Shared with me (first page)
    List<Share> findByToUserIdOrderByCreatedAtDesc(Long toUserId, Pageable pageable);

    // Shared with me (cursor pagination)
    @Query("""
            SELECT s FROM Share s
            WHERE s.toUserId = :userId
              AND (s.createdAt < :cursor OR (s.createdAt = :cursor AND s.id < :cursorId))
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<Share> findByToUserIdBeforeCursor(@Param("userId") Long userId,
                                           @Param("cursor") LocalDateTime cursor,
                                           @Param("cursorId") Long cursorId,
                                           Pageable pageable);

    // Check duplicate share
    boolean existsByFromUserIdAndToUserIdAndVideoId(Long fromUserId, Long toUserId, Long videoId);

    // Cleanup when deleting users/videos
    void deleteByFromUserId(Long userId);
    void deleteByToUserId(Long userId);
    void deleteByVideoIdIn(Collection<Long> videoIds);

    @Query("""
            SELECT s.id AS shareId,
                   s.fromUserId AS fromUserId,
                   s.toUserId AS toUserId,
                   s.videoId AS videoId,
                   v.title AS videoTitle,
                   v.coverUrl AS videoCoverUrl,
                   u.username AS creatorUsername,
                   s.createdAt AS createdAt,
                   v.videoUrl AS videoUrl,
                   u.id AS creatorUserId
            FROM Share s, Video v, User u
            WHERE s.videoId = v.id
              AND v.user.id = u.id
              AND ((s.fromUserId = :user1 AND s.toUserId = :user2)
                   OR (s.fromUserId = :user2 AND s.toUserId = :user1))
            ORDER BY s.createdAt ASC, s.id ASC
            """)
    List<Object[]> findChatHistoryBetweenUsers(@Param("user1") Long user1, @Param("user2") Long user2);
}
