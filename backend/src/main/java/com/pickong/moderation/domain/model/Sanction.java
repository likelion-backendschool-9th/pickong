package com.pickong.moderation.domain.model;

import com.pickong.common.error.BusinessException;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.vo.SanctionEndReason;
import com.pickong.moderation.domain.vo.SanctionType;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 제재 애그리거트 (WARNING · SUSPEND · BAN). suspendedUntil은 SUSPEND일 때만 있습니다.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Sanction {
    public static final Duration SEVEN_DAYS = Duration.ofHours(168);
    public static final Duration THIRTY_DAYS = Duration.ofHours(720);
    public static final int REASON_MAX = 200;

    private Long id;
    private Long userId;
    private SanctionType type;
    private String reason;
    private OffsetDateTime suspendedUntil;
    private OffsetDateTime revokedAt;
    private Long revokedByAdminId;
    private String revokeReason;
    private Long sourceReportId;
    private OffsetDateTime endedAt;
    private SanctionEndReason endReason;
    private Long adminId;
    private OffsetDateTime createdAt;

    /** API의 SUSPEND_7/SUSPEND_30 변환은 웹 경계에서 하고, 도메인에는 유형과 기간만 전달합니다. */
    public static Sanction create(long userId, SanctionType type, String reason, Duration suspensionDuration,
            long adminId, Long sourceReportId, OffsetDateTime now) {
        requirePositiveId(userId);
        requirePositiveId(adminId);
        if (sourceReportId != null) {
            requirePositiveId(sourceReportId);
        }
        requireTime(now);
        if (type == null) {
            throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_TYPE);
        }
        if (type == SanctionType.SUSPEND) {
            if (!SEVEN_DAYS.equals(suspensionDuration) && !THIRTY_DAYS.equals(suspensionDuration)) {
                throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_PERIOD);
            }
        } else if (suspensionDuration != null) {
            throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_PERIOD);
        }
        return builder().userId(userId).type(type).reason(normalizeReason(reason, true))
                .suspendedUntil(type == SanctionType.SUSPEND ? now.plus(suspensionDuration) : null)
                .adminId(adminId).sourceReportId(sourceReportId).createdAt(now).build();
    }

    /** 미종료 이력과 현재 유효한 제재는 다릅니다. 시작 전이거나 만료 시각에 도달하면 유효하지 않습니다. */
    public boolean isEffectiveAt(OffsetDateTime now) {
        requireTime(now);
        return endedAt == null && !now.isBefore(createdAt)
                && (type != SanctionType.SUSPEND || now.isBefore(suspendedUntil));
    }

    public boolean isRestrictionEffectiveAt(OffsetDateTime now) {
        return isEffectiveAt(now) && type != SanctionType.WARNING;
    }

    /** 만료되지 않았거나 이미 종료되었다면 그대로 반환합니다. 지연 처리되어도 종료 시각은 원래 만료 시각입니다. */
    public Sanction expireIfDue(OffsetDateTime now) {
        requireTime(now);
        if (endedAt != null || type != SanctionType.SUSPEND || now.isBefore(suspendedUntil)) {
            return this;
        }
        return toBuilder().endedAt(suspendedUntil).endReason(SanctionEndReason.EXPIRED).build();
    }

    /** 특정 제재의 관리자 해제. 원래 사유와 만료 시각은 변경하지 않습니다. */
    public Sanction revoke(long revokedByAdminId, String revokeReason, OffsetDateTime now) {
        requireOpenAt(now);
        requirePositiveId(revokedByAdminId);
        return toBuilder().endedAt(now).endReason(SanctionEndReason.REVOKED)
                .revokedAt(now).revokedByAdminId(revokedByAdminId)
                .revokeReason(normalizeReason(revokeReason, false)).build();
    }

    /** BAN 생성과 같은 트랜잭션에서 호출해야 합니다. 이미 만료된 정지는 EXPIRED로 먼저 정리합니다. */
    public Sanction supersedeByBan(OffsetDateTime now) {
        requireOpenAt(now);
        if (type != SanctionType.SUSPEND) {
            throw new BusinessException(ModerationErrorCode.INVALID_SANCTION_TRANSITION);
        }
        return toBuilder().endedAt(now).endReason(SanctionEndReason.SUPERSEDED_BY_BAN).build();
    }

    private void requireOpenAt(OffsetDateTime now) {
        requireTime(now);
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("제재 시작 이전에는 종료할 수 없습니다.");
        }
        if (endedAt != null) {
            throw new BusinessException(ModerationErrorCode.SANCTION_ALREADY_ENDED);
        }
        if (type == SanctionType.SUSPEND && !now.isBefore(suspendedUntil)) {
            throw new BusinessException(ModerationErrorCode.SANCTION_ALREADY_EXPIRED);
        }
    }

    /** V15 CHECK와 같은 Unicode 공백 집합을 정리하고, UTF-16 길이가 아닌 코드 포인트를 셉니다. */
    private static String normalizeReason(String raw, boolean required) {
        String value = raw == null ? "" : raw;
        int start = 0;
        int end = value.length();
        while (start < end && isReasonSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isReasonSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        value = value.substring(start, end);
        if (value.isEmpty()) {
            if (required) {
                throw new BusinessException(ModerationErrorCode.SANCTION_REASON_REQUIRED);
            }
            return null;
        }
        if (value.codePointCount(0, value.length()) > REASON_MAX) {
            throw new BusinessException(ModerationErrorCode.SANCTION_REASON_TOO_LONG);
        }
        return value;
    }

    private static boolean isReasonSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }

    private static void requirePositiveId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("양수 id가 필요합니다.");
        }
    }

    private static void requireTime(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("제재 기준 시각이 필요합니다.");
        }
    }
}
