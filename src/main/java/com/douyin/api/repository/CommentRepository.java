package com.douyin.api.repository;

import com.douyin.api.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);

    @Query("SELECT DISTINCT c.videoId FROM Comment c WHERE c.content LIKE %:q%")
    List<Long> findVideoIdsByCommentKeyword(@Param("q") String q, Pageable pageable);
}
