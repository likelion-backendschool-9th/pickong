package com.pickong.place.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitPlaceCommand;
import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.application.port.out.PlacePersistencePort;
import com.pickong.place.application.port.out.PlaceSubmissionPersistencePort;
import com.pickong.place.application.port.out.SubmitterLookupPort;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.model.Place;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSourceType;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 제보 심사 서비스. 저장소는 대역이고 DB는 쓰지 않습니다. */
@ExtendWith(MockitoExtension.class)
class PlaceSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T05:00:00Z");
    private static final GeoPoint LOCATION = new GeoPoint(37.5628, 126.9254);

    @Mock
    PlaceSubmissionPersistencePort submissions;

    @Mock
    PlacePersistencePort places;

    @Mock
    SubmitterLookupPort submitters;

    PlaceSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new PlaceSubmissionService(submissions, places, submitters, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("제보 접수는 대기 상태로 저장하고 공개 장소는 만들지 않는다")
    void submitDoesNotCreatePlace() {
        given(submissions.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(submitters.findByUserIds(any())).willReturn(Map.of());

        PlaceSubmissionView saved = service.submit(new SubmitPlaceCommand(1L, "책방", "서울", "SHOP", "설명", null, 37.5628, 126.9254));

        assertThat(saved.submission().getStatus()).isEqualTo(PlaceSubmissionStatus.PENDING);
        assertThat(saved.submission().getSubmittedAt().toInstant()).isEqualTo(NOW);
        assertThat(saved.placePublicId()).isNull();
        verify(places, never()).save(any());
    }

    @Test
    @DisplayName("범위 밖 좌표는 저장하지 않는다")
    void invalidCoordinates() {
        SubmitPlaceCommand command = new SubmitPlaceCommand(1L, "책방", null, "SHOP", null, null, 91, 0);

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
        verify(submissions, never()).save(any());
    }

    @Test
    @DisplayName("목록은 제보자와 장소 공개 id를 붙이고 대기 건의 placeId는 비운다")
    void listAttachesSubmitterAndPublicPlaceId() {
        UUID placePublicId = UUID.fromString("6f1e8c2a-1b4d-4e7a-9c3d-0a1b2c3d4e5f");
        PlaceSubmission approved = pending(2L).toBuilder()
                .status(PlaceSubmissionStatus.APPROVED)
                .placeId(9L)
                .reviewerAdminId(7L)
                .reviewedAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .build();
        given(submissions.findAll()).willReturn(List.of(approved, pending(1L)));
        given(submitters.findByUserIds(any())).willReturn(Map.of(3L, new SubmitterView("user-public", "seoyeon", "서연")));
        given(places.findPublicIdsByIds(any())).willReturn(Map.of(9L, placePublicId));

        List<PlaceSubmissionView> views = service.list();

        assertThat(views.get(0).placePublicId()).isEqualTo(placePublicId);
        assertThat(views.get(0).submitter().nickname()).isEqualTo("서연");
        assertThat(views.get(1).placePublicId()).isNull();
        assertThat(views.get(1).submitter().username()).isEqualTo("seoyeon");
    }

    @Test
    @DisplayName("없는 제보는 404")
    void missingSubmission() {
        given(submissions.findById(4L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(4L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.PLACE_SUBMISSION_NOT_FOUND);
    }

    @Test
    @DisplayName("승인은 공개 장소를 만든 뒤 대기 제보에 연결한다")
    void approveCreatesPlace() {
        PlaceSubmission pending = pending(1L);
        given(submissions.findById(1L)).willReturn(Optional.of(pending));
        given(places.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 9L));
        given(submissions.updateIfPending(any())).willReturn(true);
        given(submitters.findByUserIds(any())).willReturn(Map.of());

        PlaceSubmissionView approved = service.approve(1L, 7L);

        ArgumentCaptor<Place> place = ArgumentCaptor.forClass(Place.class);
        verify(places).save(place.capture());
        assertThat(place.getValue().getSourceType()).isEqualTo(PlaceSourceType.CUSTOM);
        assertThat(place.getValue().getName()).isEqualTo("책방");
        assertThat(place.getValue().getLocation()).isEqualTo(LOCATION);
        assertThat(approved.submission().getStatus()).isEqualTo(PlaceSubmissionStatus.APPROVED);
        assertThat(approved.submission().getPlaceId()).isEqualTo(9L);
        assertThat(approved.submission().getReviewerAdminId()).isEqualTo(7L);
        assertThat(approved.placePublicId()).isEqualTo(place.getValue().getPublicId());
    }

    @Test
    @DisplayName("장소 저장이 실패하면 제보 상태는 바꾸지 않는다")
    void placeSaveFailureDoesNotReview() {
        given(submissions.findById(1L)).willReturn(Optional.of(pending(1L)));
        given(places.save(any())).willThrow(new IllegalStateException("장소 저장 실패"));

        assertThatThrownBy(() -> service.approve(1L, 7L)).isInstanceOf(IllegalStateException.class);
        verify(submissions, never()).updateIfPending(any());
    }

    @Test
    @DisplayName("승인 반영이 한 행도 바꾸지 못하면 이미 처리된 제보로 거절한다")
    void concurrentApprove() {
        given(submissions.findById(1L)).willReturn(Optional.of(pending(1L)));
        given(places.save(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 9L));
        given(submissions.updateIfPending(any())).willReturn(false);

        assertThatThrownBy(() -> service.approve(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.ALREADY_REVIEWED);
    }

    @Test
    @DisplayName("반려는 장소를 만들지 않고 사유를 저장한다")
    void reject() {
        given(submissions.findById(1L)).willReturn(Optional.of(pending(1L)));
        given(submissions.updateIfPending(any())).willReturn(true);
        given(submitters.findByUserIds(any())).willReturn(Map.of());

        PlaceSubmissionView rejected = service.reject(1L, 7L, "  위치가 불명확합니다  ");

        assertThat(rejected.submission().getStatus()).isEqualTo(PlaceSubmissionStatus.REJECTED);
        assertThat(rejected.submission().getRejectReason()).isEqualTo("위치가 불명확합니다");
        assertThat(rejected.placePublicId()).isNull();
        verify(places, never()).save(any());
    }

    @Test
    @DisplayName("빈 반려 사유는 저장하지 않는다")
    void blankRejectReason() {
        given(submissions.findById(1L)).willReturn(Optional.of(pending(1L)));

        assertThatThrownBy(() -> service.reject(1L, 7L, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(PlaceErrorCode.REJECT_REASON_REQUIRED);
        verify(submissions, never()).updateIfPending(any());
    }

    private static PlaceSubmission pending(long id) {
        return PlaceSubmission.submit(3L, "책방", "서울", "SHOP", "설명", null, LOCATION, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .toBuilder()
                .id(id)
                .build();
    }

    private static Place withId(Place place, long id) {
        return Place.builder()
                .id(id)
                .publicId(place.getPublicId())
                .name(place.getName())
                .address(place.getAddress())
                .location(place.getLocation())
                .category(place.getCategory())
                .sourceType(place.getSourceType())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
}
