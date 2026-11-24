package com.pickong.user.application.dto;

import java.time.OffsetDateTime;

/**
 * 탈퇴를 취소할 수 있는 회원의 탈퇴 상태 (탈퇴 취소 화면).
 *
 * @param requestedAt 탈퇴를 신청한 시각
 * @param anonymizeAt 이 시각이 지나면 익명화되고 취소할 수 없음 (신청 + 30일)
 */
public record WithdrawalStatus(String username, OffsetDateTime requestedAt, OffsetDateTime anonymizeAt) {
}
