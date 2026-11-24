package com.pickong.place.domain.model;

import com.pickong.common.domain.GeoPoint;
import com.pickong.common.error.BusinessException;
import com.pickong.place.domain.PlaceErrorCode;
import com.pickong.place.domain.vo.PlaceSubmissionStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사용자가 좌표로 접수한 장소 제보. 공개 장소(Place)와 별개이며, 승인 후에만 placeId가 연결됩니다.
 * <p>값은 직접 바꾸지 않고 승인 · 반려 결과로 새 객체를 돌려줍니다. Spring · JPA · HTTP에 의존하지 않습니다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceSubmission {

    /** 설명 · 반려 사유 최대 길이. 프론트 입력 제한, mock, DB CHECK와 같습니다. */
    public static final int TEXT_MAX = 200;

    private Long id;
    private long submitterUserId;
    private String name;
    private String address;
    private String category;
    private String description;
    private String imageUrl;
    private GeoPoint location;
    private PlaceSubmissionStatus status;
    private OffsetDateTime submittedAt;
    private Long reviewerAdminId;
    private OffsetDateTime reviewedAt;
    private String rejectReason;
    /** 승인으로 만든 공개 장소 id. 대기 · 반려는 null */
    private Long placeId;

    /**
     * 심사 대기 제보를 만듭니다. 공개 장소는 만들지 않습니다.
     * <p>좌표 범위는 {@link GeoPoint}가 막습니다. 범위 밖 좌표는 이 객체가 생기기 전에 거절됩니다.
     */
    public static PlaceSubmission submit(long submitterUserId, String name, String address, String category,
            String description, String imageUrl, GeoPoint location, OffsetDateTime now) {
        if (submitterUserId <= 0) {
            throw new IllegalArgumentException("제보자 id가 필요합니다.");
        }
        if (location == null) {
            throw new BusinessException(PlaceErrorCode.SUBMISSION_NO_LOCATION);
        }
        return PlaceSubmission.builder()
                .submitterUserId(submitterUserId)
                .name(requiredText(name, PlaceErrorCode.SUBMISSION_NAME_REQUIRED))
                .address(blankToNull(address))
                .category(requiredText(category, PlaceErrorCode.CATEGORY_REQUIRED))
                .description(optionalText(description, TEXT_MAX, PlaceErrorCode.DESCRIPTION_TOO_LONG))
                .imageUrl(blankToNull(imageUrl))
                .location(location)
                .status(PlaceSubmissionStatus.PENDING)
                .submittedAt(now)
                .build();
    }

    /**
     * 대기 제보를 승인합니다. 공개 장소 id는 같은 트랜잭션에서 장소를 만든 서비스가 넘깁니다.
     * 이미 처리된 제보는 상태를 바꾸지 않습니다.
     */
    public PlaceSubmission approve(long reviewerAdminId, long placeId, OffsetDateTime now) {
        assertPending();
        if (location == null) {
            throw new BusinessException(PlaceErrorCode.SUBMISSION_NO_LOCATION);
        }
        requireIds(reviewerAdminId, placeId);
        return toBuilder()
                .status(PlaceSubmissionStatus.APPROVED)
                .reviewerAdminId(reviewerAdminId)
                .reviewedAt(now)
                .rejectReason(null)
                .placeId(placeId)
                .build();
    }

    /** 대기 제보를 반려합니다. 사유는 앞뒤 공백을 지운 뒤 비어 있으면 안 되고, 200자를 넘기면 안 됩니다. */
    public PlaceSubmission reject(long reviewerAdminId, String reason, OffsetDateTime now) {
        assertPending();
        if (reviewerAdminId <= 0) {
            throw new IllegalArgumentException("처리 관리자 id가 필요합니다.");
        }
        String trimmed = requiredText(reason, PlaceErrorCode.REJECT_REASON_REQUIRED);
        if (trimmed.codePointCount(0, trimmed.length()) > TEXT_MAX) {
            throw new BusinessException(PlaceErrorCode.REJECT_REASON_TOO_LONG);
        }
        return toBuilder()
                .status(PlaceSubmissionStatus.REJECTED)
                .reviewerAdminId(reviewerAdminId)
                .reviewedAt(now)
                .rejectReason(trimmed)
                .placeId(null)
                .build();
    }

    private void assertPending() {
        if (status != PlaceSubmissionStatus.PENDING) {
            throw new BusinessException(PlaceErrorCode.ALREADY_REVIEWED);
        }
    }

    private static void requireIds(long reviewerAdminId, long placeId) {
        if (reviewerAdminId <= 0 || placeId <= 0) {
            throw new IllegalArgumentException("처리 관리자와 장소 id가 필요합니다.");
        }
    }

    private static String requiredText(String value, PlaceErrorCode ifBlank) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ifBlank);
        }
        return value.strip();
    }

    /** 비어도 되는 글. 비어 있으면 null이고, max를 넘기면 오류입니다. */
    private static String optionalText(String value, int max, PlaceErrorCode tooLong) {
        String trimmed = blankToNull(value);
        if (trimmed != null && trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new BusinessException(tooLong);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
