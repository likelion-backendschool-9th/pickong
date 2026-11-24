package com.pickong.auth.adapter.in.web.request;

/**
 * POST /me/withdrawal 요청 본문: {@code {"reason":"RARELY_USED","detail":""}}
 * 사유 확인은 user 서비스가 하므로 여기서는 받기만 합니다. (reason: RARELY_USED · MISSING_FEATURE · PRIVACY · OTHER_SERVICE · ETC)
 */
public record WithdrawalRequest(String reason, String detail) {
}
