package com.douyin.api.repository;

import com.douyin.api.model.Danmaku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface DanmakuRepository extends JpaRepository<Danmaku, Long> {

    List<Danmaku> findByVideoIdOrderByAppearAtAscIdAsc(Long videoId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);

    @Transactional
    void deleteByUserId(Long userId);
}
