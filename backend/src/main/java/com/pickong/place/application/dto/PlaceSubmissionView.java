package com.pickong.place.application.dto;

import com.pickong.place.domain.model.PlaceSubmission;
import java.util.UUID;

/**
 * 관리자 목록 · 상세에 필요한 제보.
 * placePublicId는 승인으로 만든 장소의 공개 id이고, 대기 · 반려는 null입니다.
 */
public record PlaceSubmissionView(PlaceSubmission submission, SubmitterView submitter, UUID placePublicId) {
}
