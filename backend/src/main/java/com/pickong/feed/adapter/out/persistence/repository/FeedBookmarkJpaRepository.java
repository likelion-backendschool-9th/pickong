package com.pickong.feed.adapter.out.persistence.repository;

import com.pickong.feed.adapter.out.persistence.entity.FeedBookmarkJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedBookmarkJpaRepository extends JpaRepository<FeedBookmarkJpaEntity, Long> {

    boolean existsByFeedIdAndUserId(Long feedId, Long userId);

    void deleteByFeedIdAndUserId(Long feedId, Long userId);
}
