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
              AND c.parentId IS NULL
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
              AND c.parentId IS NULL
              AND (c.createdAt < :cursorCreatedAt OR (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CommentItemProjection> findTopLevelByVideoIdBeforeCursor(
            @Param("videoId") Long videoId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
