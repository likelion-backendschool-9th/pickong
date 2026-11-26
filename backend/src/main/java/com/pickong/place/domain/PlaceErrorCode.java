package com.pickong.place.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 장소(place) 모듈 오류 코드. 화면은 message를 그대로 표시합니다. */
@Getter
public enum PlaceErrorCode implements ErrorCode {

    PLACE_SUBMISSION_NOT_FOUND(404, "제보를 찾을 수 없어요."),
    /** 대기 상태가 아닌 제보를 다시 승인하거나 반려함 */
    ALREADY_REVIEWED(409, "이미 처리한 제보예요."),
    SUBMISSION_NAME_REQUIRED(400, "장소 이름을 입력해 주세요."),
    CATEGORY_REQUIRED(400, "카테고리를 선택해 주세요."),
    DESCRIPTION_TOO_LONG(400, "설명은 200자까지 쓸 수 있어요."),
    SUBMISSION_NO_LOCATION(400, "좌표가 없어 승인할 수 없어요."),
    /** 위도 · 경도 범위 밖. GeoPoint가 거부한 값을 화면 오류로 바꿉니다. */
    INVALID_COORDINATES(400, "위치를 지정해 주세요."),
    /** 지도 장소 조회 범위가 너무 넓음 (MapBoundsPolicy) */
    BOUNDS_TOO_LARGE(400, "지도를 조금 더 확대해 주세요."),
    REJECT_REASON_REQUIRED(400, "반려 사유를 입력해 주세요."),
    REJECT_REASON_TOO_LONG(400, "반려 사유는 200자까지 쓸 수 있어요.");

    private final int status;
    private final String message;

    PlaceErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
