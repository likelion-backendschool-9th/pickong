package com.pickong.place.adapter.in.web.admin;

import com.pickong.common.domain.GeoPoint;
import com.pickong.place.application.dto.PlaceSubmissionView;
import com.pickong.place.application.dto.SubmitterView;
import com.pickong.place.domain.model.PlaceSubmission;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.OffsetDateTime;

/** 관리자 제보 목록 · 상세. 프론트 PlaceSubmissionDto와 같은 필드입니다. */
public record PlaceSubmissionResponse(
        String id,
        String name,
        String category,
        String address,
        Double lat,
        Double lng,
        String description,
        String imageUrl,
        SubmitterResponse submitter,
        OffsetDateTime submittedAt,
        PlaceSubmissionStatus status,
        OffsetDateTime reviewedAt,
        String rejectReason,
        String placeId) {

    /** 제보자. id는 회원 공개 id입니다. */
    public record SubmitterResponse(String id, String username, String nickname) {

        private static SubmitterResponse from(SubmitterView submitter) {
            if (submitter == null) {
                return null;
            }
            return new SubmitterResponse(submitter.id(), submitter.username(), submitter.nickname());
        }
    }

    public static PlaceSubmissionResponse from(PlaceSubmissionView view) {
        PlaceSubmission submission = view.submission();
        GeoPoint location = submission.getLocation();
        return new PlaceSubmissionResponse(
                Long.toString(submission.getId()),
                submission.getName(),
                submission.getCategory(),
                submission.getAddress(),
                location == null ? null : location.latitude(),
                location == null ? null : location.longitude(),
                submission.getDescription(),
                submission.getImageUrl(),
                SubmitterResponse.from(view.submitter()),
                submission.getSubmittedAt(),
                submission.getStatus(),
                submission.getReviewedAt(),
                submission.getRejectReason(),
                view.placePublicId() == null ? null : view.placePublicId().toString());
    }
}
