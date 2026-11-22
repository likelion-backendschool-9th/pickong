package com.pickong.feed.application.service;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
        // given: 저장소가 Feed를 반환하도록 설정
        CreateFeedCommand command = new CreateFeedCommand(1L, "테스트 게시물", Visibility.PUBLIC, List.of(1L), List.of(10L));
        given(feedRepository.save(any(Feed.class))).willReturn(sampleFeed());

        // when
        Feed result = feedService.createFeed(command);

        // then: 반환된 피드 id·내용 확인, save() 호출 여부 확인
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("테스트 게시물");
        verify(feedRepository).save(any(Feed.class));
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
        assertThatThrownBy(() -> feedService.updateFeed(999L,
                new UpdateFeedCommand("수정 내용", Visibility.PUBLIC, List.of(), List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteFeed_존재하지않는id_예외발생() {
        // given: 없는 id 삭제 시 BusinessException 던지도록 설정
        willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND))
                .given(feedRepository).deleteById(999L);

        // when & then
        assertThatThrownBy(() -> feedService.deleteFeed(999L))
                .isInstanceOf(BusinessException.class);
    }
}
