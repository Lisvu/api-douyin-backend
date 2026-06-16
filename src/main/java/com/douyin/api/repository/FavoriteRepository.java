package com.douyin.api.repository;

import com.douyin.api.model.Favorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);

    Optional<Favorite> findByUserIdAndVideoId(Long userId, Long videoId);
    boolean existsByUserIdAndVideoId(Long userId, Long videoId);
    long countByVideoId(Long videoId);

    @Query("select f.videoId from Favorite f where f.userId = :userId and f.videoId in :videoIds")
    Set<Long> findFavoritedVideoIds(@Param("userId") Long userId, @Param("videoIds") Collection<Long> videoIds);

    @Query("select f.videoId, count(f) from Favorite f where f.videoId in :videoIds group by f.videoId")
    List<Object[]> countGroupByVideoIds(@Param("videoIds") Collection<Long> videoIds);

    List<Favorite> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT f FROM Favorite f
            WHERE f.userId = :userId
              AND (f.createdAt < :cursorCreatedAt OR (f.createdAt = :cursorCreatedAt AND f.id < :cursorId))
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Favorite> findByUserIdBeforeCursor(@Param("userId") Long userId,
                                            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                                            @Param("cursorId") Long cursorId,
                                            Pageable pageable);
}
