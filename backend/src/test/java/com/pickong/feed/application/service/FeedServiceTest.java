package com.pickong.feed.application.service;

import com.pickong.common.domain.Visibility;
import com.pickong.common.error.BusinessException;
import com.pickong.feed.domain.FeedErrorCode;
import com.pickong.feed.application.dto.CreateFeedCommand;
import com.pickong.feed.application.dto.FeedPage;
import com.pickong.feed.application.dto.FeedPlaceGroup;
import com.pickong.feed.application.dto.FeedPlaceSummary;
import com.pickong.feed.application.dto.UpdateFeedCommand;
import com.pickong.feed.application.port.out.FeedPlaceQueryPort;
import com.pickong.feed.application.port.out.FeedRepository;
import com.pickong.feed.domain.model.Feed;
import com.pickong.user.application.port.in.UserQueryUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.pickong.social.application.port.in.RelationQueryUseCase;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.OffsetDateTime;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedPlaceQueryPort feedPlaceQueryPort;

    @Mock
    private UserQueryUseCase userQueryUseCase;

    // 관계를 정해 주지 않으면 null → 도메인이 ANONYMOUS로 판정 (PUBLIC 게시물만 통과)
    @Mock
    private RelationQueryUseCase relationQueryUseCase;

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
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
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
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());

        // when & then
        assertThatThrownBy(() -> feedService.updateFeed(1L, 99L,
                new UpdateFeedCommand("수정 내용", Visibility.PUBLIC, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("피드 작성자만 수정·삭제할 수 있습니다");
    }

    @Test
    void deleteFeed_성공() {
        // given: 피드가 존재하고 본인 피드
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedRepository.softDelete(1L)).willReturn(true);

        // when
        feedService.deleteFeed(1L, 1L);

        // then: 행을 지우지 않고 소프트 삭제만 호출됐는지 확인
        verify(feedRepository).softDelete(1L);
    }

    @Test
    void deleteFeed_이미삭제된피드_404_소프트삭제안함() {
        given(feedRepository.findByIdForUpdate(1L)).willReturn(
                Feed.builder().id(1L).userId(1L).deletedAt(OffsetDateTime.now()).build());

        assertThatThrownBy(() -> feedService.deleteFeed(1L, 1L))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
        verify(feedRepository, never()).softDelete(1L);
    }

    @Test
    void deleteFeed_타인의삭제된피드_403이아니라404() {
        // 이미 삭제된 글이면 작성자가 아니어도 FEED_NOT_OWNER 대신 없는 글과 같은 404
        given(feedRepository.findByIdForUpdate(1L)).willReturn(
                Feed.builder().id(1L).userId(1L).deletedAt(OffsetDateTime.now()).build());

        assertThatThrownBy(() -> feedService.deleteFeed(1L, 99L))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
    }

    @Test
    void deleteFeed_소프트삭제가반영안되면_404() {
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());
        given(feedRepository.softDelete(1L)).willReturn(false);

        assertThatThrownBy(() -> feedService.deleteFeed(1L, 1L))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
    }

    @Test
    void updateFeed_삭제된피드_404() {
        given(feedRepository.findByIdForUpdate(1L)).willReturn(
                Feed.builder().id(1L).userId(1L).deletedAt(OffsetDateTime.now()).build());

        assertThatThrownBy(() -> feedService.updateFeed(1L, 1L,
                new UpdateFeedCommand("수정", Visibility.PUBLIC, List.of(), List.of())))
                .extracting("errorCode").isEqualTo(FeedErrorCode.FEED_NOT_FOUND);
        verify(feedRepository, never()).save(any());
    }

    @Test
    void deleteFeed_타인피드_삭제시_예외발생() {
        // given: 피드 작성자는 userId=1, 요청자는 userId=99
        given(feedRepository.findByIdForUpdate(1L)).willReturn(sampleFeed());

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
        given(feedRepository.findByIdForUpdate(999L))
                .willThrow(new BusinessException(FeedErrorCode.FEED_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedService.updateFeed(999L, 1L,
                new UpdateFeedCommand("수정 내용", Visibility.PUBLIC, List.of(), List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteFeed_존재하지않는id_예외발생() {
        // given: 없는 id 조회 시 BusinessException 던지도록 설정
        given(feedRepository.findByIdForUpdate(999L))
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

    // ── getFeedPlaces ──────────────────────────────────────────────

    @Test
    void getFeedPlaces_잘못된_tab값_400() {
        // recommended·following 외 값은 INVALID_TAB 예외
        assertThatThrownBy(() -> feedService.getFeedPlaces("popular", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("tab은 recommended 또는 following만 허용됩니다");
    }

    @Test
    void getFeedPlaces_recommended_성공() {
        // given: 장소 2개가 연결된 피드 행 반환
        FeedPlaceSummary row1 = new FeedPlaceSummary(1L, "uuid-1", "카페A", "서울", 37.1, 127.1, "CAFE", "MANUAL", 10L);
        FeedPlaceSummary row2 = new FeedPlaceSummary(2L, "uuid-2", "식당B", "부산", 35.1, 129.1, "RESTAURANT", "MANUAL", 11L);
        given(feedPlaceQueryPort.findRecommendedFeedPlaces()).willReturn(List.of(row1, row2));

        // when
        List<FeedPlaceGroup> result = feedService.getFeedPlaces("recommended", null);

        // then: 장소 2개, 각각 피드 1개씩
        assertThat(result).hasSize(2);
    }

    @Test
    void getFeedPlaces_following_비로그인_빈목록() {
        // userId null이면 쿼리 호출 없이 바로 빈 목록 반환
        List<FeedPlaceGroup> result = feedService.getFeedPlaces("following", null);
        assertThat(result).isEmpty();
        // DB 쿼리가 아예 나가지 않아야 함
        verify(feedPlaceQueryPort, never()).findFollowingFeedPlaces(any());
    }

    @Test
    void getFeedPlaces_같은장소_여러피드_그룹핑() {
        // given: 같은 장소(placeId=1)에 피드 2개 연결
        FeedPlaceSummary row1 = new FeedPlaceSummary(1L, "uuid-1", "카페A", "서울", 37.1, 127.1, "CAFE", "MANUAL", 10L);
        FeedPlaceSummary row2 = new FeedPlaceSummary(1L, "uuid-1", "카페A", "서울", 37.1, 127.1, "CAFE", "MANUAL", 11L);
        given(feedPlaceQueryPort.findRecommendedFeedPlaces()).willReturn(List.of(row1, row2));

        // when
        List<FeedPlaceGroup> result = feedService.getFeedPlaces("recommended", null);

        // then: 장소 1개, feedIds 2개
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFeedIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    // ── getUserFeeds ───────────────────────────────────────────────

    @Test
    void getUserFeeds_없는_username_404() {
        // given: UserQueryUseCase가 USER_NOT_FOUND 던짐
        given(userQueryUseCase.findIdByUsername("없는유저"))
                .willThrow(new BusinessException(com.pickong.user.domain.UserErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> feedService.getUserFeeds("없는유저", null, null, 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getUserFeeds_본인이면_전체_visibility_조회() {
        // given: 본인(viewerUserId=1) 조회 → isOwner=true → findByOwner(1L, true, ...)
        given(userQueryUseCase.findIdByUsername("user1")).willReturn(1L);
        given(feedRepository.findByOwner(eq(1L), eq(true), any(), anyInt()))
                .willReturn(List.of(sampleFeed()));

        // when
        FeedPage page = feedService.getUserFeeds("user1", 1L, null, 10);

        // then
        assertThat(page.getFeeds()).hasSize(1);
        verify(feedRepository).findByOwner(eq(1L), eq(true), any(), anyInt());
    }

    @Test
    void getUserFeeds_타인이면_PUBLIC만_조회() {
        // given: 타인(viewerUserId=99) 조회 → isOwner=false → findByOwner(1L, false, ...)
        given(userQueryUseCase.findIdByUsername("user1")).willReturn(1L);
        given(feedRepository.findByOwner(eq(1L), eq(false), any(), anyInt()))
                .willReturn(List.of(sampleFeed()));

        // when
        FeedPage page = feedService.getUserFeeds("user1", 99L, null, 10);

        // then
        assertThat(page.getFeeds()).hasSize(1);
        verify(feedRepository).findByOwner(eq(1L), eq(false), any(), anyInt());
    }

    @Test
    void getUserFeeds_비로그인이면_PUBLIC만_조회() {
        // given: 비로그인(viewerUserId=null) → isOwner=false
        given(userQueryUseCase.findIdByUsername("user1")).willReturn(1L);
        given(feedRepository.findByOwner(eq(1L), eq(false), any(), anyInt()))
                .willReturn(List.of());

        // when
        FeedPage page = feedService.getUserFeeds("user1", null, null, 10);

        // then
        verify(feedRepository).findByOwner(eq(1L), eq(false), any(), anyInt());
        assertThat(page.getFeeds()).isEmpty();
    }
}
