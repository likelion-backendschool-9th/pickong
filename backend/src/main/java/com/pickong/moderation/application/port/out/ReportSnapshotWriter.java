package com.pickong.moderation.application.port.out;

import com.pickong.moderation.domain.model.ReportSnapshot;

/** 스냅샷을 reports.snapshot 컬럼에 넣을 JSON 문자열로 바꿉니다. */
public interface ReportSnapshotWriter {

    String write(ReportSnapshot snapshot);
}
