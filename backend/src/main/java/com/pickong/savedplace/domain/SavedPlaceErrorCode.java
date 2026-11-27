package com.pickong.savedplace.domain;

import com.pickong.common.error.ErrorCode;
import lombok.Getter;

/** 저장 장소(savedplace) 모듈 오류 코드. 화면은 message를 그대로 표시합니다. */
@Getter
public enum SavedPlaceErrorCode implements ErrorCode {

    /** 저장할 장소가 하나도 없음 */
    NO_PLACE_SELECTED(400, "저장할 장소를 선택해 주세요."),
    /** 한 번에 다루는 개수를 넘음 (SavedPlaceCommandService.MAX_IDS_PER_REQUEST) */
    TOO_MANY_PLACES(400, "한 번에 너무 많은 장소를 고르셨어요."),
    /** 없거나 볼 수 없는 장소 (삭제됨, 다른 회원의 비공개 장소) */
    PLACE_NOT_FOUND(404, "장소를 찾을 수 없어요."),
    INVALID_SOURCE(400, "저장 경로를 확인해 주세요."),
    INVALID_VISIT_STATUS(400, "방문 상태를 확인해 주세요."),
    MEMO_TOO_LONG(400, "메모는 500자까지 쓸 수 있어요."),
    /**
     * 저장하면서 컬렉션에 담기(collectionIds, add · set · removeCollectionIds)는 아직 없습니다 (8.1 #13).
     * 받고 무시하면 성공처럼 보이므로 명시적으로 거절합니다. 빈 목록은 받습니다.
     */
    COLLECTION_LINK_NOT_SUPPORTED(400, "컬렉션에 담기는 아직 지원하지 않아요.");

    private final int status;
    private final String message;

    SavedPlaceErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
