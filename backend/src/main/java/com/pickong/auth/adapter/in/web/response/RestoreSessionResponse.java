package com.pickong.auth.adapter.in.web.response;

import com.pickong.user.application.dto.WithdrawalStatus;
import java.time.OffsetDateTime;

/**
 * GET /auth/withdrawal 응답 (탈퇴 취소 화면).
 * {@code {"username":"seoyeon","requestedAt":"2026-11-25T01:00:00Z","anonymizeAt":"2026-12-25T01:00:00Z"}}
 */
public record RestoreSessionResponse(String username, OffsetDateTime requestedAt, OffsetDateTime anonymizeAt) {

    public static RestoreSessionResponse from(WithdrawalStatus status) {
        return new RestoreSessionResponse(status.username(), status.requestedAt(), status.anonymizeAt());
    }
}
