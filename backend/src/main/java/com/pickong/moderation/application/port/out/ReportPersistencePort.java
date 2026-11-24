package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportDismissal;
import com.pickong.moderation.domain.model.Report;
import com.pickong.moderation.domain.vo.ReportStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 신고 저장소. 다른 모듈 테이블은 바꾸지 않습니다. */
public interface ReportPersistencePort {

    Optional<Report> findById(long id);

    /** 접수 시각 내림차순, 같으면 id 내림차순. */
    List<Report> findByStatus(ReportStatus status);

    /**
     * 선택한 신고가 아직 대기일 때만, 같은 대상의 대기 신고를 함께 기각합니다.
     * 잠금은 대상 행을 id 오름차순으로 잡아 동시에 들어온 기각이 서로를 덮거나 교착되지 않게 합니다.
     */
    ReportDismissal dismissIfPrimaryPending(long reportId, long adminId, String memo, OffsetDateTime now);
}
