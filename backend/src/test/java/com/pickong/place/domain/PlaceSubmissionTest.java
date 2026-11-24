package com.pickong.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 장소 제보 상태 전이 단위 테스트. Spring · DB 없이 실행합니다. */
class PlaceSubmissionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T05:00:00Z");
    private static final GeoPoint LOCATION = new GeoPoint(37.5628, 126.9254);

    @Test
    @DisplayName("접수한 제보는 심사 대기이고 공개 장소와 처리 정보가 없다")
    void submitStartsPending() {
        PlaceSubmission submission = submit("  연남 골목 책방  ", "  골목 안쪽  ", "  ");

        assertThat(submission.getStatus()).isEqualTo(PlaceSubmissionStatus.PENDING);
        assertThat(submission.getName()).isEqualTo("연남 골목 책방");
        assertThat(submission.getDescription()).isEqualTo("골목 안쪽");
        assertThat(submission.getImageUrl()).isNull();
        assertThat(submission.getPlaceId()).isNull();
        assertThat(submission.getReviewerAdminId()).isNull();
        assertThat(submission.getReviewedAt()).isNull();
        assertThat(submission.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이름이 없거나 카테고리가 없거나 설명이 200자를 넘으면 접수되지 않는다")
    void submitRejectsInvalidText() {
        assertError(() -> submit("  ", "설명", null), PlaceErrorCode.SUBMISSION_NAME_REQUIRED);
        assertError(
                () -> PlaceSubmission.submit(1L, "책방", null, " ", null, null, LOCATION, NOW),
                PlaceErrorCode.CATEGORY_REQUIRED);
        assertError(() -> submit("책방", "가".repeat(201), null), PlaceErrorCode.DESCRIPTION_TOO_LONG);
    }

    @Test
    @DisplayName("좌표가 없으면 접수와 승인이 거절된다. 범위 밖 좌표는 GeoPoint가 받지 않는다")
    void locationRequired() {
        assertError(
                () -> PlaceSubmission.submit(1L, "책방", null, "SHOP", null, null, null, NOW),
                PlaceErrorCode.SUBMISSION_NO_LOCATION);
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);

        PlaceSubmission withoutLocation = PlaceSubmission.builder()
                .submitterUserId(1L)
                .name("책방")
                .category("SHOP")
                .status(PlaceSubmissionStatus.PENDING)
                .submittedAt(NOW)
                .build();
        assertError(() -> withoutLocation.approve(7L, 3L, NOW), PlaceErrorCode.SUBMISSION_NO_LOCATION);
    }

    @Test
    @DisplayName("승인하면 처리 관리자, 처리 시각, 공개 장소가 연결되고 반려 사유는 비운다")
    void approveLinksPlace() {
        PlaceSubmission approved = submit("책방", "설명", null).approve(7L, 3L, NOW.plusHours(1));

        assertThat(approved.getStatus()).isEqualTo(PlaceSubmissionStatus.APPROVED);
        assertThat(approved.getReviewerAdminId()).isEqualTo(7L);
        assertThat(approved.getReviewedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(approved.getPlaceId()).isEqualTo(3L);
        assertThat(approved.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("반려는 사유를 다듬어 저장하고 공개 장소는 연결하지 않는다")
    void rejectStoresReason() {
        PlaceSubmission rejected = submit("책방", null, null).reject(7L, "  위치가 불명확합니다  ", NOW);

        assertThat(rejected.getStatus()).isEqualTo(PlaceSubmissionStatus.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("위치가 불명확합니다");
        assertThat(rejected.getReviewerAdminId()).isEqualTo(7L);
        assertThat(rejected.getPlaceId()).isNull();
    }

    @Test
    @DisplayName("반려 사유가 없거나 공백만 있거나 200자를 넘으면 반려되지 않는다")
    void rejectReasonRequired() {
        PlaceSubmission pending = submit("책방", null, null);

        assertError(() -> pending.reject(7L, "   ", NOW), PlaceErrorCode.REJECT_REASON_REQUIRED);
        assertError(() -> pending.reject(7L, null, NOW), PlaceErrorCode.REJECT_REASON_REQUIRED);
        assertError(() -> pending.reject(7L, "가".repeat(201), NOW), PlaceErrorCode.REJECT_REASON_TOO_LONG);
        assertThat(pending.getStatus()).isEqualTo(PlaceSubmissionStatus.PENDING);
    }

    @Test
    @DisplayName("승인했거나 반려한 제보는 다시 승인하거나 반려할 수 없다")
    void alreadyReviewed() {
        PlaceSubmission pending = submit("책방", null, null);
        PlaceSubmission approved = pending.approve(7L, 3L, NOW);
        PlaceSubmission rejected = pending.reject(7L, "위치가 불명확합니다", NOW);

        assertError(() -> approved.approve(8L, 4L, NOW), PlaceErrorCode.ALREADY_REVIEWED);
        assertError(() -> approved.reject(8L, "다시 반려", NOW), PlaceErrorCode.ALREADY_REVIEWED);
        assertError(() -> rejected.approve(8L, 4L, NOW), PlaceErrorCode.ALREADY_REVIEWED);
        assertError(() -> rejected.reject(8L, "다른 사유", NOW), PlaceErrorCode.ALREADY_REVIEWED);
        assertThat(approved.getPlaceId()).isEqualTo(3L);
        assertThat(rejected.getRejectReason()).isEqualTo("위치가 불명확합니다");
    }

    private static PlaceSubmission submit(String name, String description, String imageUrl) {
        return PlaceSubmission.submit(1L, name, "서울 마포구", "SHOP", description, imageUrl, LOCATION, NOW);
    }

    private static void assertError(ThrowingCallable executable, PlaceErrorCode code) {
        assertThatThrownBy(executable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }
}
