package com.douyin.api.repository;

import com.douyin.api.model.WatchLater;
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

@Repository
public interface WatchLaterRepository extends JpaRepository<WatchLater, Long> {

    Optional<WatchLater> findByUserIdAndVideoId(Long userId, Long videoId);

    boolean existsByUserIdAndVideoId(Long userId, Long videoId);

    long countByUserId(Long userId);

    List<WatchLater> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT wl FROM WatchLater wl
            WHERE wl.userId = :userId
              AND (wl.createdAt < :createdAt OR (wl.createdAt = :createdAt AND wl.id < :id))
            ORDER BY wl.createdAt DESC, wl.id DESC
            """)
    List<WatchLater> findByUserIdBeforeCursor(
            @Param("userId") Long userId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("id") Long id,
            Pageable pageable);

    @Transactional
    void deleteByUserIdAndVideoId(Long userId, Long videoId);

    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);
}
