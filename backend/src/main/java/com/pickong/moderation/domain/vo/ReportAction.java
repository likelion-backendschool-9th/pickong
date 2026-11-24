package com.pickong.moderation.domain.vo;

/**
 * 신고를 어떻게 닫았는지. 상태(PENDING · RESOLVED)와 별개입니다.
 * <p>지금은 기각만 저장합니다. 숨김과 제재는 대상 모듈의 공개 기능이 생긴 뒤에 처리합니다.
 */
public enum ReportAction {
    DISMISS,
    HIDE_CONTENT,
    SANCTION_USER
}
