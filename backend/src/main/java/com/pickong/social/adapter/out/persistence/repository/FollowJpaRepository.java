package com.pickong.social.adapter.out.persistence.repository;

import com.pickong.social.adapter.out.persistence.entity.FollowJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowJpaRepository extends JpaRepository<FollowJpaEntity, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    int deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Query("SELECT f.followeeId FROM FollowJpaEntity f WHERE f.followerId = :followerId")
    List<Long> findFolloweeIdsByFollowerId(@Param("followerId") Long followerId);
}
