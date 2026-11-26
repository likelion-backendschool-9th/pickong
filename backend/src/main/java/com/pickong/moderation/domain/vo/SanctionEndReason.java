package com.pickong.moderation.domain.vo;

/** 자동 만료, 관리자 해제, 기간 정지의 영구 정지 전환을 구분합니다. */
public enum SanctionEndReason {
    EXPIRED,
    REVOKED,
    SUPERSEDED_BY_BAN
}
