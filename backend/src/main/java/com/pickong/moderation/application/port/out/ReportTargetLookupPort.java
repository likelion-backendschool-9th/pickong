package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.util.Optional;

/**
 * 회원 신고 접수에 쓸 대상 조회.
 * 없거나 회원이 볼 수 없으면 빈 값입니다. 두 경우를 응답에서 나누지 않습니다.
 */
public interface ReportTargetLookupPort {

    /**
     * 신고 대상이 될 수 있는 게시물 또는 댓글을 읽음 (없음, 삭제, 숨김은 빈 값)
     * 신고자가 볼 수 있는지(공개 범위)는 판단하지 않음. 접수 서비스가 feed 모듈에 물어봄
     */
    Optional<ReportTargetLookup> findReportable(ReportTargetType targetType, long targetId);
}
