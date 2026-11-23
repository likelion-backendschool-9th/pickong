package com.pickong.feed.adapter.out.persistence;

import com.pickong.feed.adapter.out.persistence.entity.FeedBookmarkJpaEntity;
import com.pickong.feed.adapter.out.persistence.entity.FeedLikeJpaEntity;
import com.pickong.feed.adapter.out.persistence.repository.FeedBookmarkJpaRepository;
import com.pickong.feed.adapter.out.persistence.repository.FeedLikeJpaRepository;
import com.pickong.feed.application.port.out.FeedReactionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaFeedReactionAdapter implements FeedReactionRepository {

    private final FeedLikeJpaRepository feedLikeJpaRepository;
    private final FeedBookmarkJpaRepository feedBookmarkJpaRepository;

    public JpaFeedReactionAdapter(FeedLikeJpaRepository feedLikeJpaRepository,
                                  FeedBookmarkJpaRepository feedBookmarkJpaRepository) {
        this.feedLikeJpaRepository = feedLikeJpaRepository;
        this.feedBookmarkJpaRepository = feedBookmarkJpaRepository;
    }

    @Override
    public boolean existsLike(Long feedId, Long userId) {
        return feedLikeJpaRepository.existsByFeedIdAndUserId(feedId, userId);
    }

    @Override
    public void saveLike(Long feedId, Long userId) {
        feedLikeJpaRepository.save(FeedLikeJpaEntity.of(feedId, userId));
    }

    @Override
    public void deleteLike(Long feedId, Long userId) {
        feedLikeJpaRepository.deleteByFeedIdAndUserId(feedId, userId);
    }

    @Override
    public boolean existsBookmark(Long feedId, Long userId) {
        return feedBookmarkJpaRepository.existsByFeedIdAndUserId(feedId, userId);
    }

    @Override
    public void saveBookmark(Long feedId, Long userId) {
        feedBookmarkJpaRepository.save(FeedBookmarkJpaEntity.of(feedId, userId));
    }

    @Override
    public void deleteBookmark(Long feedId, Long userId) {
        feedBookmarkJpaRepository.deleteByFeedIdAndUserId(feedId, userId);
    }
}
