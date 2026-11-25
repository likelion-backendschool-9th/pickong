package com.pickong.user.domain.event;

/** 동기 처리 요청. moderation이 유효한 제재를 확인하기 전에는 user가 스스로 ACTIVE로 복구하지 않습니다. */
public record UserSuspensionExpiryCheckEvent(long userId) {
}
