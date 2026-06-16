package com.douyin.api.repository;

import com.douyin.api.model.Like;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndVideoId(Long userId, Long videoId);
    boolean existsByUserIdAndVideoId(Long userId, Long videoId);
    void deleteByUserId(Long userId);
    void deleteByVideoId(Long videoId);
    void deleteByVideoIdIn(Collection<Long> videoIds);

    @Query("select l.videoId from Like l where l.userId = :userId and l.videoId in :videoIds")
    Set<Long> findLikedVideoIds(@Param("userId") Long userId, @Param("videoIds") Collection<Long> videoIds);

    // Cursor pagination: get all liked videos for a user (for "我的喜欢")
    List<Like> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT l FROM Like l
            WHERE l.userId = :userId
              AND (l.createdAt < :cursorCreatedAt OR (l.createdAt = :cursorCreatedAt AND l.id < :cursorId))
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<Like> findByUserIdBeforeCursor(@Param("userId") Long userId,
                                        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                                        @Param("cursorId") Long cursorId,
                                        Pageable pageable);

    @Query("""
            SELECT l.id AS likeId,
                   l.userId AS likerUserId,
                   u.username AS likerUsername,
                   u.displayName AS likerDisplayName,
                   u.avatarUrl AS likerAvatarUrl,
                   l.videoId AS videoId,
                   v.title AS videoTitle,
                   l.createdAt AS likedAt
            FROM Like l, User u, Video v
            WHERE l.userId = u.id
              AND l.videoId = v.id
              AND v.user.id = :ownerId
              AND l.userId <> :ownerId
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LikeNotificationProjection> findReceivedLikeNotificationsCursor(
            @Param("ownerId") Long ownerId,
            Pageable pageable);

    @Query("""
            SELECT l.id AS likeId,
                   l.userId AS likerUserId,
                   u.username AS likerUsername,
                   u.displayName AS likerDisplayName,
                   u.avatarUrl AS likerAvatarUrl,
                   l.videoId AS videoId,
                   v.title AS videoTitle,
                   l.createdAt AS likedAt
            FROM Like l, User u, Video v
            WHERE l.userId = u.id
              AND l.videoId = v.id
              AND v.user.id = :ownerId
              AND l.userId <> :ownerId
              AND (l.createdAt < :cursorCreatedAt OR (l.createdAt = :cursorCreatedAt AND l.id < :cursorId))
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<LikeNotificationProjection> findReceivedLikeNotificationsBeforeCursor(
            @Param("ownerId") Long ownerId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(l)
            FROM Like l, Video v
            WHERE l.videoId = v.id
              AND v.user.id = :ownerId
              AND l.userId <> :ownerId
            """)
    long countReceivedLikes(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT COUNT(l)
            FROM Like l, Video v
            WHERE l.videoId = v.id
              AND v.user.id = :ownerId
              AND l.userId <> :ownerId
              AND l.createdAt > :readAfter
            """)
    long countReceivedLikesAfter(@Param("ownerId") Long ownerId, @Param("readAfter") LocalDateTime readAfter);
}
