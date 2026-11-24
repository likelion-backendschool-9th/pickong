package com.pickong.feed.application.service;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @InjectMocks
    private FeedService feedService;

    // 샘플 Feed 객체 생성 헬퍼
    private Feed sampleFeed() {
        return Feed.builder()
                .id(1L)
                .userId(1L)
                .content("테스트 게시물")
                .visibility(Visibility.PUBLIC)
                .build();
    }

    @Test
    void createFeed_성공() {
        // given
        CreateFeedCommand command = new CreateFeedCommand(1L, "테스트 게시물", Visibility.PUBLIC, List.of(1L), List.of(10L));
        given(feedRepository.save(any(Feed.class))).willReturn(sampleFeed());

        // when
        Feed result = feedService.createFeed(command);

        // then: 반환된 피드 id·내용 확인
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("테스트 게시물");
    }

    @Test
    void createFeed_저장되는_피드_내용이_커맨드와_일치() {
        // given
        CreateFeedCommand command = new CreateFeedCommand(1L, "테스트 게시물", Visibility.PUBLIC, List.of(1L), List.of(10L));
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        given(feedRepository.save(captor.capture())).willReturn(sampleFeed());

        // when
        feedService.createFeed(command);

        // then: save()에 전달된 Feed의 필드가 command 값과 일치하는지 검증
        Feed saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getContent()).isEqualTo("테스트 게시물");
        assertThat(saved.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateFeed_성공() {
        // given
        UpdateFeedCommand command = new UpdateFeedCommand("수정된 내용", Visibility.PRIVATE, List.of(), List.of());
        given(feedRepository.findById(1L)).willReturn(sampleFeed());
        given(feedRepository.save(any(Feed.class))).willReturn(
                Feed.builder().id(1L).userId(1L).content("수정된 내용").visibility(Visibility.PRIVATE).build());

        // when
        Feed result = feedService.updateFeed(1L, 1L, command);

        // then
        assertThat(result.getContent()).isEqualTo("수정된 내용");
        assertThat(result.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void updateFeed_타인피드_수정시_예외발생() {
        // given: 피드 작성자는 userId=1, 요청자는 userId=99
        given(feedRepository.findById(1L)).willReturn(sampleFeed());

        // when & then
        assertThatThrownBy(() -> feedService.updateFeed(1L, 99L,
                new UpdateFeedCommand("수정 내용", Visibility.PUBLIC, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드 작성자만 수정·삭제할 수 있습니다");
    }

    @Test
    void deleteFeed_성공() {
        // given: 피드가 존재하고 본인 피드
        given(feedRepository.findById(1L)).willReturn(sampleFeed());
        willDoNothing().given(feedRepository).deleteById(1L);

        // when
        feedService.deleteFeed(1L, 1L);

        // then: deleteById()가 호출됐는지 확인
        verify(feedRepository).deleteById(1L);
    }

    @Test
    void deleteFeed_타인피드_삭제시_예외발생() {
        // given: 피드 작성자는 userId=1, 요청자는 userId=99
        given(feedRepository.findById(1L)).willReturn(sampleFeed());

        // when & then
        assertThatThrownBy(() -> feedService.deleteFeed(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드 작성자만 수정·삭제할 수 있습니다");
    }

    @Test
    void getFeed_존재하지않는id_예외발생() {
        // given: 없는 id 조회 시 BusinessException 던지도록 설정
        given(feedRepository.findById(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedService.getFeed(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드를 찾을 수 없습니다");
    }

    @Test
    void updateFeed_존재하지않는id_예외발생() {
        // given: 없는 id 조회 시 BusinessException 던지도록 설정
        given(feedRepository.findById(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedService.updateFeed(999L, 1L,
                new UpdateFeedCommand("수정 내용", Visibility.PUBLIC, List.of(), List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteFeed_존재하지않는id_예외발생() {
        // given: 없는 id 조회 시 BusinessException 던지도록 설정
        given(feedRepository.findById(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedService.deleteFeed(999L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getRecommendedFeeds_성공() {
        // given
        given(feedRepository.findRecommendedFeeds(null, null, 11))
                .willReturn(List.of(sampleFeed()));
        // when
        FeedPage page = feedService.getRecommendedFeeds(null, null, 10);

        // then
        assertThat(page.getFeeds()).hasSize(1);
        assertThat(page.isHasNext()).isFalse();
    }

    @Test
    void getFollowingFeeds_성공() {
        given(feedRepository.findFollowingFeeds(1L, null, 11))
                .willReturn(List.of(sampleFeed()));
        FeedPage page = feedService.getFollowingFeeds(1L, null, 10);

        assertThat(page.getFeeds()).hasSize(1);
        assertThat(page.isHasNext()).isFalse();
    }

    @Test
    void getFollowingFeeds_비로그인_빈피드반환() {
        FeedPage page = feedService.getFollowingFeeds(null, null, 10);
        assertThat(page.getFeeds()).isEmpty();
    }
}
