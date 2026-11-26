package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
import com.pickong.feed.application.port.out.FeedReactionRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedReactionService implements FeedReactionUseCase {

    private final FeedReactionRepository feedReactionRepository;
    private final FeedRepository feedRepository;

    public FeedReactionService(FeedReactionRepository feedReactionRepository,
                               FeedRepository feedRepository) {
        this.feedReactionRepository = feedReactionRepository;
        this.feedRepository = feedRepository;
    }

    @Override
    @Transactional
    public void likeFeed(Long feedId, Long userId) {
        // 피드 존재 여부 확인 — 없으면 FEED_NOT_FOUND 예외
        feedRepository.findByIdForUpdate(feedId).ensureAcceptsInteraction();
        if (feedReactionRepository.existsLike(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED);
        }
        feedReactionRepository.saveLike(feedId, userId);
        feedRepository.incrementLikeCount(feedId);
    }

    @Override
    @Transactional
    public void unlikeFeed(Long feedId, Long userId) {
        feedRepository.findByIdForUpdate(feedId);
        if (!feedReactionRepository.existsLike(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.LIKE_NOT_FOUND);
        }
        feedReactionRepository.deleteLike(feedId, userId);
        feedRepository.decrementLikeCount(feedId);
    }

    @Override
    @Transactional
    public void bookmarkFeed(Long feedId, Long userId) {
        feedRepository.findByIdForUpdate(feedId).ensureAcceptsInteraction();
        if (feedReactionRepository.existsBookmark(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.ALREADY_BOOKMARKED);
        }
        feedReactionRepository.saveBookmark(feedId, userId);
        feedRepository.incrementBookmarkCount(feedId);
    }

    @Override
    @Transactional
    public void unbookmarkFeed(Long feedId, Long userId) {
        feedRepository.findByIdForUpdate(feedId);
        if (!feedReactionRepository.existsBookmark(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.BOOKMARK_NOT_FOUND);
        }
        feedReactionRepository.deleteBookmark(feedId, userId);
        feedRepository.decrementBookmarkCount(feedId);
    }
}
