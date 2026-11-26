package com.pickong.moderation.adapter.in.web.admin;

import com.pickong.moderation.application.dto.AdminUserView;
import com.pickong.moderation.application.dto.AdminUserView.ActiveSanction;
import com.pickong.moderation.domain.model.Sanction;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내부 id는 JSON 문자열입니다. 계정 상태는 DB 원본(탈퇴 상태 포함)을 유지합니다.
 * isCreator는 크리에이터 테이블·조회 기반이 생기기 전까지 null(확인 불가)입니다.
 */
public record AdminUserResponse(
        String id, String username, String nickname, String profileImageUrl, Boolean isCreator,
        long reportCount, long postCount, String status, SanctionResponse sanction, List<SanctionResponse> sanctions) {

    public static AdminUserResponse from(AdminUserView user) {
        List<SanctionResponse> sanctions = user.sanctions().stream().map(SanctionResponse::from).toList();
        return new AdminUserResponse(Long.toString(user.id()), user.username(), user.nickname(), user.profileImageUrl(),
                null, user.reportCount(), user.postCount(), user.status(),
                sanctions.isEmpty() ? null : sanctions.getFirst(), sanctions);
    }

    /** 이용 제한 우선, 그다음 최신 경고 순서입니다. startedAt은 기존 created_at입니다. */
    public record SanctionResponse(String id, String type, String reason, OffsetDateTime startedAt, OffsetDateTime until) {
        /** 등록 결과도 목록과 같은 제재 표현을 사용합니다. */
        public static SanctionResponse from(Sanction sanction) {
            return from(new ActiveSanction(sanction.getId(), sanction.getType(), sanction.getReason(),
                    sanction.getCreatedAt(), sanction.getSuspendedUntil()));
        }

        private static SanctionResponse from(ActiveSanction sanction) {
            String type = switch (sanction.type()) {
                case WARNING -> "WARNING";
                case BAN -> "BAN";
                case SUSPEND -> {
                    Duration duration = Duration.between(sanction.startedAt(), sanction.until());
                    if (Sanction.SEVEN_DAYS.equals(duration)) {
                        yield "SUSPEND_7";
                    }
                    if (Sanction.THIRTY_DAYS.equals(duration)) {
                        yield "SUSPEND_30";
                    }
                    throw new IllegalStateException("지원하지 않는 제재 기간입니다: " + sanction.id());
                }
            };
            return new SanctionResponse(Long.toString(sanction.id()), type, sanction.reason(), sanction.startedAt(), sanction.until());
        }
    }
}
