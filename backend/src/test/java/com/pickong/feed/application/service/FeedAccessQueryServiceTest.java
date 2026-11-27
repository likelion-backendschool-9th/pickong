package com.pickong.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.pickong.common.domain.ViewerRelation;
import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.domain.model.Feed;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 다른 모듈용 열람 확인: 상세 조회와 같은 판단을 예외 대신 true, false로 돌려주는지
@ExtendWith(MockitoExtension.class)
class FeedAccessQueryServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private RelationQueryUseCase relationQueryUseCase;

    @InjectMocks
    private FeedAccessQueryService service;

    @Test
    void 팔로워는_FOLLOWERS_게시물을_볼_수_있음() {
        given(feedRepository.findById(1L)).willReturn(feed(Visibility.FOLLOWERS));
        given(relationQueryUseCase.relationOf(7L, 3L)).willReturn(ViewerRelation.FOLLOWER);

        assertThat(service.isReadableBy(1L, 7L)).isTrue();
    }

    @Test
    void 팔로워가_아니면_FOLLOWERS_게시물은_false() {
        given(feedRepository.findById(1L)).willReturn(feed(Visibility.FOLLOWERS));
        given(relationQueryUseCase.relationOf(7L, 3L)).willReturn(ViewerRelation.OTHER);

        assertThat(service.isReadableBy(1L, 7L)).isFalse();
    }

    @Test
    void 없는_게시물은_예외_대신_false() {
        given(feedRepository.findById(999L)).willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        assertThat(service.isReadableBy(999L, 7L)).isFalse();
    }

    @Test
    void 삭제된_게시물은_작성자도_false() {
        given(feedRepository.findById(1L)).willReturn(
                Feed.builder().id(1L).userId(3L).visibility(Visibility.PUBLIC).deletedAt(OffsetDateTime.now()).build());
        given(relationQueryUseCase.relationOf(3L, 3L)).willReturn(ViewerRelation.OWNER);

        assertThat(service.isReadableBy(1L, 3L)).isFalse();
    }

    private static Feed feed(Visibility visibility) {
        return Feed.builder().id(1L).userId(3L).visibility(visibility).build();
    }
}
