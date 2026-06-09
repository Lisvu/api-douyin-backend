package com.douyin.api.repository;

import com.douyin.api.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
}
