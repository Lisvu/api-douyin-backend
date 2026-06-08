package com.douyin.api.repository;

import com.douyin.api.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByUserId(Long userId);

    // Find all videos by a specific user with pagination, ordered by creation date descending
    @EntityGraph(attributePaths = {"user"})
    Page<Video> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Recommender Query: Find videos that this user has NOT viewed yet, ordered by likesCount DESC (paginated)
    @Query("SELECT v FROM Video v JOIN FETCH v.user WHERE v.id NOT IN (SELECT vi.videoId FROM View vi WHERE vi.userId = :userId) ORDER BY v.likesCount DESC")
    List<Video> findRecommendedVideosForUser(@Param("userId") Long userId, Pageable pageable);

    // Scalar query: get only likesCount without loading the Video entity
    @Query("SELECT v.likesCount FROM Video v WHERE v.id = :id")
    Optional<Integer> findLikesCountById(@Param("id") Long id);

    // Atomic likesCount update (no entity loading needed)
    @Modifying
    @Query("UPDATE Video v SET v.likesCount = v.likesCount + :delta WHERE v.id = :id")
    int incrementLikesCount(@Param("id") Long id, @Param("delta") int delta);
}
