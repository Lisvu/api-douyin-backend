package com.douyin.api.repository;

import com.douyin.api.model.UserRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserRelationRepository extends JpaRepository<UserRelation, Long> {
    @Transactional
    void deleteByFollowerIdOrFollowingId(Long followerId, Long followingId);
}
