package com.douyin.api.repository;

import com.douyin.api.model.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);

    @Query("SELECT DISTINCT c.videoId FROM Comment c WHERE c.content LIKE %:q%")
    List<Long> findVideoIdsByCommentKeyword(@Param("q") String q, Pageable pageable);
    long countByVideoId(Long videoId);

    @Query("""
            SELECT c.videoId, COUNT(c)
            FROM Comment c
            WHERE c.videoId IN :videoIds
            GROUP BY c.videoId
            """)
    List<Object[]> countGroupByVideoIds(@Param("videoIds") Collection<Long> videoIds);

    @Query("""
            SELECT c.id AS id,
                   c.videoId AS videoId,
                   c.userId AS userId,
                   u.username AS username,
                   u.displayName AS displayName,
                   u.avatarUrl AS avatarUrl,
                   c.content AS content,
                   c.createdAt AS createdAt
            FROM Comment c, User u
            WHERE c.userId = u.id
              AND c.videoId = :videoId
              AND (c.parentId IS NULL OR c.parentId = 0)
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CommentItemProjection> findTopLevelByVideoId(@Param("videoId") Long videoId, Pageable pageable);

    @Query("""
            SELECT c.id AS id,
                   c.videoId AS videoId,
                   c.userId AS userId,
                   u.username AS username,
                   u.displayName AS displayName,
                   u.avatarUrl AS avatarUrl,
                   c.content AS content,
                   c.createdAt AS createdAt
            FROM Comment c, User u
            WHERE c.userId = u.id
              AND c.videoId = :videoId
              AND (c.parentId IS NULL OR c.parentId = 0)
              AND (c.createdAt < :cursorCreatedAt OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CommentItemProjection> findTopLevelByVideoIdBeforeCursor(
            @Param("videoId") Long videoId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT c.id AS commentId,
                   c.userId AS commenterUserId,
                   u.username AS commenterUsername,
                   u.displayName AS commenterDisplayName,
                   u.avatarUrl AS commenterAvatarUrl,
                   c.videoId AS videoId,
                   v.title AS videoTitle,
                   c.content AS commentContent,
                   c.createdAt AS commentedAt
            FROM Comment c, User u, Video v
            WHERE c.userId = u.id
              AND c.videoId = v.id
              AND v.user.id = :ownerId
              AND c.userId <> :ownerId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Object[]> findReceivedCommentNotificationsCursor(
            @Param("ownerId") Long ownerId,
            Pageable pageable);

    @Query("""
            SELECT c.id AS commentId,
                   c.userId AS commenterUserId,
                   u.username AS commenterUsername,
                   u.displayName AS commenterDisplayName,
                   u.avatarUrl AS commenterAvatarUrl,
                   c.videoId AS videoId,
                   v.title AS videoTitle,
                   c.content AS commentContent,
                   c.createdAt AS commentedAt
            FROM Comment c, User u, Video v
            WHERE c.userId = u.id
              AND c.videoId = v.id
              AND v.user.id = :ownerId
              AND c.userId <> :ownerId
              AND (c.createdAt < :cursorCreatedAt OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Object[]> findReceivedCommentNotificationsBeforeCursor(
            @Param("ownerId") Long ownerId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(c)
            FROM Comment c, Video v
            WHERE c.videoId = v.id
              AND v.user.id = :ownerId
              AND c.userId <> :ownerId
            """)
    long countReceivedComments(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT COUNT(c)
            FROM Comment c, Video v
            WHERE c.videoId = v.id
              AND v.user.id = :ownerId
              AND c.userId <> :ownerId
              AND c.createdAt > :readAfter
            """)
    long countReceivedCommentsAfter(@Param("ownerId") Long ownerId, @Param("readAfter") LocalDateTime readAfter);
}
