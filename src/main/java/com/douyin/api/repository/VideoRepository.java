package com.douyin.api.repository;

import com.douyin.api.model.Video;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByUserId(Long userId);

    List<Video> findByTitleStartingWithOrderByIdAsc(String titlePrefix);

    @EntityGraph(attributePaths = {"user"})
    List<Video> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("""
            SELECT v FROM Video v
            WHERE v.user.id = :userId
              AND (v.createdAt < :cursorCreatedAt OR (v.createdAt = :cursorCreatedAt AND v.id < :cursorId))
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<Video> findByUserIdBeforeCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // Recommender Query: Find videos that this user has NOT viewed yet, ordered by likesCount DESC (paginated)
    @Query("""
            SELECT v FROM Video v JOIN FETCH v.user
            WHERE v.id NOT IN (SELECT vi.videoId FROM View vi WHERE vi.userId = :userId)
              AND v.id IN (
                  SELECT MAX(v2.id) FROM Video v2
                  WHERE v2.id NOT IN (SELECT vi2.videoId FROM View vi2 WHERE vi2.userId = :userId)
                  GROUP BY v2.videoUrl
              )
            ORDER BY COALESCE(v.likesCount, 0) DESC, v.id DESC
            """)
    List<Video> findRecommendedVideosForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT v FROM Video v JOIN FETCH v.user
            WHERE v.id NOT IN (SELECT vi.videoId FROM View vi WHERE vi.userId = :userId)
              AND v.id IN (
                  SELECT MAX(v2.id) FROM Video v2
                  WHERE v2.id NOT IN (SELECT vi2.videoId FROM View vi2 WHERE vi2.userId = :userId)
                  GROUP BY v2.videoUrl
              )
              AND (COALESCE(v.likesCount, 0) < :cursorLikesCount
                   OR (COALESCE(v.likesCount, 0) = :cursorLikesCount AND v.id < :cursorId))
            ORDER BY COALESCE(v.likesCount, 0) DESC, v.id DESC
            """)
    List<Video> findRecommendedVideosForUserAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorLikesCount") int cursorLikesCount,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // Scalar query: get only likesCount without loading the Video entity
    @Query("SELECT v.likesCount FROM Video v WHERE v.id = :id")
    Optional<Integer> findLikesCountById(@Param("id") Long id);

    // Atomic likesCount update (no entity loading needed)
    @Modifying
    @Query("UPDATE Video v SET v.likesCount = v.likesCount + :delta WHERE v.id = :id")
    int incrementLikesCount(@Param("id") Long id, @Param("delta") int delta);

    // Find videos by a set of IDs (for liked-videos and shared-videos panels)
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT v FROM Video v WHERE v.id IN :ids ORDER BY v.createdAt DESC")
    List<Video> findByIdInOrderByCreatedAtDesc(@Param("ids") Collection<Long> ids);
}
