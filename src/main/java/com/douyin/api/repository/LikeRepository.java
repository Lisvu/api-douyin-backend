package com.douyin.api.repository;

import com.douyin.api.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndVideoId(Long userId, Long videoId);
    boolean existsByUserIdAndVideoId(Long userId, Long videoId);
    void deleteByUserId(Long userId);
    void deleteByVideoId(Long videoId);
    void deleteByVideoIdIn(Collection<Long> videoIds);
}
