package com.pickong.moderation.domain.vo;

/**
 * 신고를 어떻게 닫았는지. 상태(PENDING · RESOLVED)와 별개입니다.
 * <p>처리 효과는 공통 제재 또는 콘텐츠 소유 모듈의 공개 포트로 수행합니다.
 */
public enum ReportAction {
    DISMISS,
    HIDE_CONTENT,
    SANCTION_USER
}
