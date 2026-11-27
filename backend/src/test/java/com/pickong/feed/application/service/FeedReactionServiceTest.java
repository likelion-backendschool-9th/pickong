package com.pickong.feed.application.service;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.port.out.FeedReactionRepository;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

// Spring 없이 Mockito만으로 서비스 로직 단위 테스트
@ExtendWith(MockitoExtension.class)
class FeedReactionServiceTest {

    @Mock
    private FeedReactionRepository feedReactionRepository;

    @Mock
    private FeedRepository feedRepository;

    // 관계를 정해 주지 않으면 null → 도메인이 ANONYMOUS로 판정 (PUBLIC 게시물만 통과)
    @Mock
    private RelationQueryUseCase relationQueryUseCase;

    @InjectMocks
    private FeedReactionService feedReactionService;

    // 테스트용 샘플 Feed
    private Feed sampleFeed() {
        return Feed.builder()
                .id(1L)
                .userId(1L)
                .content("테스트 게시물")
                .visibility(Visibility.PUBLIC)
                .build();
    }

    // ─────────────────────────────────────────────
    //  likeFeed
    // ─────────────────────────────────────────────

    @Test
    void likeFeed_성공() {
        // given: 피드 존재, 아직 좋아요 없음
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsLike(1L, 1L)).willReturn(false);

        // when
        feedReactionService.likeFeed(1L, 1L);

        // then: saveLike()가 정확히 1번 호출되어야 함
        verify(feedReactionRepository).saveLike(1L, 1L);
    }

    @Test
    void likeFeed_피드없음_예외발생() {
        // given: 피드 조회 시 FEED_NOT_FOUND 발생
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedReactionService.likeFeed(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.FEED_NOT_FOUND.getMessage());
    }

    @Test
    void likeFeed_이미좋아요_예외발생() {
        // given: 피드 존재, 이미 좋아요 눌린 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsLike(1L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> feedReactionService.likeFeed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.ALREADY_LIKED.getMessage());
    }

    // ─────────────────────────────────────────────
    //  unlikeFeed
    // ─────────────────────────────────────────────

    @Test
    void unlikeFeed_성공() {
        // given: 피드 존재, 좋아요 눌린 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsLike(1L, 1L)).willReturn(true);

        // when
        feedReactionService.unlikeFeed(1L, 1L);

        // then: deleteLike()가 정확히 1번 호출되어야 함
        verify(feedReactionRepository).deleteLike(1L, 1L);
    }

    @Test
    void unlikeFeed_피드없음_예외발생() {
        // given: 피드 조회 시 FEED_NOT_FOUND 발생
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedReactionService.unlikeFeed(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.FEED_NOT_FOUND.getMessage());
    }

    @Test
    void unlikeFeed_좋아요없음_예외발생() {
        // given: 피드 존재, 좋아요 안 눌린 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsLike(1L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> feedReactionService.unlikeFeed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.LIKE_NOT_FOUND.getMessage());
    }

    // ─────────────────────────────────────────────
    //  bookmarkFeed
    // ─────────────────────────────────────────────

    @Test
    void bookmarkFeed_성공() {
        // given: 피드 존재, 아직 북마크 없음
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsBookmark(1L, 1L)).willReturn(false);

        // when
        feedReactionService.bookmarkFeed(1L, 1L);

        // then: saveBookmark()가 정확히 1번 호출되어야 함
        verify(feedReactionRepository).saveBookmark(1L, 1L);
    }

    @Test
    void bookmarkFeed_피드없음_예외발생() {
        // given: 피드 조회 시 FEED_NOT_FOUND 발생
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedReactionService.bookmarkFeed(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.FEED_NOT_FOUND.getMessage());
    }

    @Test
    void bookmarkFeed_이미북마크_예외발생() {
        // given: 피드 존재, 이미 북마크된 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsBookmark(1L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> feedReactionService.bookmarkFeed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.ALREADY_BOOKMARKED.getMessage());
    }

    // ─────────────────────────────────────────────
    //  unbookmarkFeed
    // ─────────────────────────────────────────────

    @Test
    void unbookmarkFeed_성공() {
        // given: 피드 존재, 북마크된 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsBookmark(1L, 1L)).willReturn(true);

        // when
        feedReactionService.unbookmarkFeed(1L, 1L);

        // then: deleteBookmark()가 정확히 1번 호출되어야 함
        verify(feedReactionRepository).deleteBookmark(1L, 1L);
    }

    @Test
    void unbookmarkFeed_피드없음_예외발생() {
        // given: 피드 조회 시 FEED_NOT_FOUND 발생
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedReactionService.unbookmarkFeed(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.FEED_NOT_FOUND.getMessage());
    }

    @Test
    void unbookmarkFeed_북마크없음_예외발생() {
        // given: 피드 존재, 북마크 안 된 상태
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedReactionRepository.existsBookmark(1L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> feedReactionService.unbookmarkFeed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FeedErrorCode.BOOKMARK_NOT_FOUND.getMessage());
    }
}
