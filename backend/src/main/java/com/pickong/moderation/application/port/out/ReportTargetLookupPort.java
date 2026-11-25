package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.util.Optional;

/**
 * 회원 신고 접수에 쓸 대상 조회.
 * 없거나 회원이 볼 수 없으면 빈 값입니다. 두 경우를 응답에서 나누지 않습니다.
 */
public interface ReportTargetLookupPort {

    Optional<ReportTargetLookup> findReportable(long reporterId, ReportTargetType targetType, long targetId);
}
