package com.douyin.api.repository;

import com.douyin.api.model.UserRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRelationRepository extends JpaRepository<UserRelation, Long> {

    // 注销账号时清理关系
    @Transactional
    void deleteByFollowerIdOrFollowingId(Long followerId, Long followingId);

    // 查询是否已关注
    Optional<UserRelation> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // 取关
    @Transactional
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    // 我关注的人（我是 follower）
    List<UserRelation> findByFollowerId(Long followerId);

    // 我的粉丝（我是 following）
    List<UserRelation> findByFollowingId(Long followingId);

    // 互关好友：我关注了对方，且对方也关注了我
    @Query("""
            SELECT r FROM UserRelation r
            WHERE r.followerId = :userId
              AND r.followingId IN (
                  SELECT r2.followerId FROM UserRelation r2
                  WHERE r2.followingId = :userId
              )
            """)
    List<UserRelation> findMutualFollows(@Param("userId") Long userId);

    // 统计我关注的人数
    long countByFollowerId(Long followerId);

    // 统计我的粉丝数
    long countByFollowingId(Long followingId);
}