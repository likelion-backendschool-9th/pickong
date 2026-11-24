package com.pickong.social.adapter.out.persistence;

import com.pickong.social.adapter.out.persistence.entity.FollowJpaEntity;
import com.pickong.social.adapter.out.persistence.repository.FollowJpaRepository;
import com.pickong.social.application.port.out.RelationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JpaRelationAdapter implements RelationRepository {

    private final FollowJpaRepository followJpaRepository;

    public JpaRelationAdapter(FollowJpaRepository followJpaRepository) {
        this.followJpaRepository = followJpaRepository;
    }

    @Override
    @Transactional
    public void saveFollow(Long followerId, Long followeeId) {
        followJpaRepository.save(FollowJpaEntity.of(followerId, followeeId));
    }

    @Override
    @Transactional
    public boolean deleteFollow(Long followerId, Long followeeId) {
        return followJpaRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId) > 0;
    }

    @Override
    public boolean existsFollow(Long followerId, Long followeeId) {
        return followJpaRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public List<Long> findFollowingIds(Long followerId) {
        return followJpaRepository.findFolloweeIdsByFollowerId(followerId);
    }
}
