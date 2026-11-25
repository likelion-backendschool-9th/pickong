package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportStatus;
import com.pickong.moderation.domain.vo.ReportTargetType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 신고 저장소. 다른 모듈 테이블은 바꾸지 않습니다. */
public interface ReportPersistencePort {

    Optional<Report> findById(long id);

    /** 같은 신고자 · 대상의 대기 신고가 있는지. 사유는 보지 않습니다. */
    boolean existsPending(long reporterId, ReportTargetType targetType, long targetId);

    /**
     * 대기 신고를 새로 저장합니다. 같은 신고자 · 대상의 대기 신고가 이미 있으면
     * {@code DUPLICATE_REPORT}로 거절합니다. 다른 DB 오류는 그대로 둡니다.
     */
    Report save(Report report);

    /** 접수 시각 내림차순, 같으면 id 내림차순. */
    List<Report> findByStatus(ReportStatus status);

    /**
     * 선택한 신고가 아직 대기일 때만 그 신고만 기각합니다.
     * 같은 대상의 다른 신고는 바꾸지 않습니다.
     * 행 잠금으로 같은 신고를 동시에 기각하면 한 번만 성공합니다.
     */
    ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now);
}
