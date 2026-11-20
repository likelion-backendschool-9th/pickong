package com.pickong.moderation.domain.model;

import com.pickong.moderation.domain.vo.SanctionType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 회원 제재 애그리거트 (WARNING · SUSPEND · BAN). suspendedUntil은 SUSPEND일 때만 있습니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Sanction {
    private Long id;
    private Long userId;
    private SanctionType type;
    private String reason;
    private OffsetDateTime suspendedUntil;
    private OffsetDateTime revokedAt;
    private Long adminId;
    private OffsetDateTime createdAt;
}
