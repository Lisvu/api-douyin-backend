package com.douyin.api.repository;

import com.douyin.api.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    
    // Find all videos by a specific user with pagination, ordered by creation date descending
    Page<Video> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Recommender Query: Find all videos that this user has NOT viewed yet, ordered by likesCount DESC
    @Query("SELECT v FROM Video v WHERE v.id NOT IN (SELECT vi.videoId FROM View vi WHERE vi.userId = :userId) ORDER BY v.likesCount DESC")
    List<Video> findRecommendedVideosForUser(@Param("userId") Long userId);
}
