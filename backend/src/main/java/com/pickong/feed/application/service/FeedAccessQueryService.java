package com.pickong.feed.application.service;

import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.port.in.FeedAccessQueryUseCase;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈(신고 접수 등)이 게시물 열람 가능 여부를 물어볼 때 답함
 * 상세 조회(FeedService.getFeed)와 같은 Feed.ensureReadable로 판단하고, 예외 대신 true, false로 돌려줌
 * 그래서 부르는 모듈이 자기 오류 코드(예: REPORT_TARGET_NOT_FOUND)로 응답할 수 있음
 */
@Service
public class FeedAccessQueryService implements FeedAccessQueryUseCase {

    private final FeedRepository feedRepository;
    private final RelationQueryUseCase relationQueryUseCase;

    public FeedAccessQueryService(FeedRepository feedRepository, RelationQueryUseCase relationQueryUseCase) {
        this.feedRepository = feedRepository;
        this.relationQueryUseCase = relationQueryUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReadableBy(Long feedId, Long viewerId) {
        try {
            Feed feed = feedRepository.findById(feedId);
            feed.ensureReadable(relationQueryUseCase.relationOf(viewerId, feed.getUserId()), false);
            return true;
        } catch (BusinessException notReadable) {
            // 없는 게시물(findById)과 볼 수 없는 게시물(ensureReadable) 모두 FEED_NOT_FOUND
            return false;
        }
    }
}
