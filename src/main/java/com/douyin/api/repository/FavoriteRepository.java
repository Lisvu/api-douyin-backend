package com.douyin.api.repository;

import com.douyin.api.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByVideoIdIn(Collection<Long> videoIds);
}
