package com.pickong.moderation.application.port.out;

import com.pickong.moderation.application.dto.ReportTargetPreview;
import com.pickong.moderation.domain.model.Report;
import java.util.List;
import java.util.Map;

/** 신고 대상 미리보기. 대상이 없어도 목록 전체가 실패하지 않도록 신고마다 값을 돌려줍니다. */
public interface ReportTargetPreviewPort {

    /**
     * 키는 신고 id입니다. 같은 대상의 신고도 각자의 미리보기를 갖습니다.
     * 저장된 id가 없는 신고는 맵에 넣지 않습니다. 빈 목록은 조회하지 않고 빈 맵을 돌려줍니다.
     */
    Map<Long, ReportTargetPreview> find(List<Report> reports);
}
