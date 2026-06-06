package com.douyin.api.repository;

import com.douyin.api.model.View;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ViewRepository extends JpaRepository<View, Long> {
    Optional<View> findByUserIdAndVideoId(Long userId, Long videoId);
    boolean existsByUserIdAndVideoId(Long userId, Long videoId);

    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM View v WHERE v.videoId = :videoId")
    void deleteByVideoId(@Param("videoId") Long videoId);
}
