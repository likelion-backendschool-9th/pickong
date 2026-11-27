package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.in.FeedReactionUseCase;
import com.pickong.feed.application.port.out.FeedReactionRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedReactionService implements FeedReactionUseCase {

    private final FeedReactionRepository feedReactionRepository;
    private final FeedRepository feedRepository;
    private final RelationQueryUseCase relationQueryUseCase;

    public FeedReactionService(FeedReactionRepository feedReactionRepository,
                               FeedRepository feedRepository,
                               RelationQueryUseCase relationQueryUseCase) {
        this.feedReactionRepository = feedReactionRepository;
        this.feedRepository = feedRepository;
        this.relationQueryUseCase = relationQueryUseCase;
    }

    @Override
    @Transactional
    public void likeFeed(Long feedId, Long userId) {
        lockAcceptingInteraction(feedId, userId);
        if (feedReactionRepository.existsLike(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.ALREADY_LIKED);
        }
        feedReactionRepository.saveLike(feedId, userId);
        feedRepository.incrementLikeCount(feedId);
    }

    @Override
    @Transactional
    public void unlikeFeed(Long feedId, Long userId) {
        lockReadable(feedId, userId);
        if (!feedReactionRepository.existsLike(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.LIKE_NOT_FOUND);
        }
        feedReactionRepository.deleteLike(feedId, userId);
        feedRepository.decrementLikeCount(feedId);
    }

    @Override
    @Transactional
    public void bookmarkFeed(Long feedId, Long userId) {
        lockAcceptingInteraction(feedId, userId);
        if (feedReactionRepository.existsBookmark(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.ALREADY_BOOKMARKED);
        }
        feedReactionRepository.saveBookmark(feedId, userId);
        feedRepository.incrementBookmarkCount(feedId);
    }

    @Override
    @Transactional
    public void unbookmarkFeed(Long feedId, Long userId) {
        lockReadable(feedId, userId);
        if (!feedReactionRepository.existsBookmark(feedId, userId)) {
            throw new BusinessException(FeedErrorCode.BOOKMARK_NOT_FOUND);
        }
        feedReactionRepository.deleteBookmark(feedId, userId);
        feedRepository.decrementBookmarkCount(feedId);
    }

    // 좋아요, 북마크 추가: 볼 수 있고 숨김이 아닌 게시물만. 아니면 FEED_NOT_FOUND
    private void lockAcceptingInteraction(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        feed.ensureAcceptsInteraction(relationQueryUseCase.relationOf(userId, feed.getUserId()));
    }

    // 좋아요, 북마크 취소: 볼 수 있는 게시물만. 볼 수 없는 글에서 LIKE_NOT_FOUND가 나가면 글이 있다는 게 드러남
    private void lockReadable(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdForUpdate(feedId);
        feed.ensureReadable(relationQueryUseCase.relationOf(userId, feed.getUserId()), false);
    }
}
