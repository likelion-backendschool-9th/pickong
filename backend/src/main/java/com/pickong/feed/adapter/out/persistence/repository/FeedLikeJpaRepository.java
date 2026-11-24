package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedLikeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedLikeJpaRepository extends JpaRepository<FeedLikeJpaEntity, Long> {

    boolean existsByFeedIdAndUserId(Long feedId, Long userId);

    void deleteByFeedIdAndUserId(Long feedId, Long userId);
}
