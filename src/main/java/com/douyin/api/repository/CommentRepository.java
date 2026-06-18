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
                   c.parentId AS parentId,
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
                   c.parentId AS parentId,
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
            SELECT c.id AS id,
                   c.videoId AS videoId,
                   c.userId AS userId,
                   c.parentId AS parentId,
                   u.username AS username,
                   u.displayName AS displayName,
                   u.avatarUrl AS avatarUrl,
                   c.content AS content,
                   c.createdAt AS createdAt
            FROM Comment c, User u
            WHERE c.userId = u.id
              AND c.parentId IN :parentIds
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<CommentItemProjection> findRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    @Query("""
            SELECT c.id AS id,
                   c.videoId AS videoId,
                   c.userId AS userId,
                   c.parentId AS parentId,
                   u.username AS username,
                   u.displayName AS displayName,
                   u.avatarUrl AS avatarUrl,
                   c.content AS content,
                   c.createdAt AS createdAt
            FROM Comment c, User u, Comment directParent
            WHERE c.userId = u.id
              AND c.parentId = directParent.id
              AND (c.parentId IN :parentIds OR directParent.parentId IN :parentIds)
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<CommentItemProjection> findRepliesByRootParentIds(@Param("parentIds") Collection<Long> parentIds);

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
            SELECT c.id AS replyId,
                   c.userId AS replierUserId,
                   u.username AS replierUsername,
                   u.displayName AS replierDisplayName,
                   u.avatarUrl AS replierAvatarUrl,
                   c.videoId AS videoId,
                   v.title AS videoTitle,
                   c.content AS replyContent,
                   c.createdAt AS repliedAt,
                   parent.id AS parentCommentId,
                   parent.content AS parentContent
            FROM Comment c, Comment parent, User u, Video v
            WHERE c.userId = u.id
              AND c.parentId = parent.id
              AND c.videoId = v.id
              AND parent.userId = :ownerId
              AND c.userId <> :ownerId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Object[]> findReceivedReplyNotificationsCursor(
            @Param("ownerId") Long ownerId,
            Pageable pageable);

    @Query("""
            SELECT c.id AS replyId,
                   c.userId AS replierUserId,
                   u.username AS replierUsername,
                   u.displayName AS replierDisplayName,
                   u.avatarUrl AS replierAvatarUrl,
                   c.videoId AS videoId,
                   v.title AS videoTitle,
                   c.content AS replyContent,
                   c.createdAt AS repliedAt,
                   parent.id AS parentCommentId,
                   parent.content AS parentContent
            FROM Comment c, Comment parent, User u, Video v
            WHERE c.userId = u.id
              AND c.parentId = parent.id
              AND c.videoId = v.id
              AND parent.userId = :ownerId
              AND c.userId <> :ownerId
              AND (c.createdAt < :cursorCreatedAt OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Object[]> findReceivedReplyNotificationsBeforeCursor(
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

    @Query("""
            SELECT COUNT(c)
            FROM Comment c, Comment parent
            WHERE c.parentId = parent.id
              AND parent.userId = :ownerId
              AND c.userId <> :ownerId
            """)
    long countReceivedReplies(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT COUNT(c)
            FROM Comment c, Comment parent
            WHERE c.parentId = parent.id
              AND parent.userId = :ownerId
              AND c.userId <> :ownerId
              AND c.createdAt > :readAfter
            """)
    long countReceivedRepliesAfter(@Param("ownerId") Long ownerId, @Param("readAfter") LocalDateTime readAfter);
}
