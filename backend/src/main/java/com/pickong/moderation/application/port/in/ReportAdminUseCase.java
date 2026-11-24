package com.pickong.moderation.application.port.in;

import com.pickong.moderation.application.dto.ReportView;
import java.util.List;

/** 관리자 신고 목록과 기각. 숨김 · 제재 실행은 이 포트가 성공으로 끝내지 않습니다. */
public interface ReportAdminUseCase {

    /** 상태 하나. 접수 시각 내림차순, 같으면 id 내림차순. 없으면 빈 목록. */
    List<ReportView> list(String status);

    /**
     * 대기 신고를 처리합니다. 기각만 저장하고, 숨김 · 제재는 상태를 바꾸지 않은 채 거절합니다.
     * 관리자 id는 인증 주체에서 온 값만 받습니다.
     */
    ReportView resolve(long reportId, long adminId, String action, String memo, String sanctionType);
}
