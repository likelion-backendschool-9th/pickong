package com.pickong.moderation.adapter.out.snapshot;

import com.pickong.moderation.application.port.out.ReportSnapshotWriter;
import com.pickong.moderation.domain.model.ReportSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 전체 본문 스냅샷을 기존 관리자 파서가 읽는 JSON으로 씁니다. 본문은 바꾸지 않습니다. */
@Component
public class ReportSnapshotJsonAdapter implements ReportSnapshotWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String write(ReportSnapshot snapshot) {
        ObjectNode node = JSON.createObjectNode();
        if (snapshot.text() == null) {
            node.putNull("text");
        } else {
            node.put("text", snapshot.text());
        }
        node.putNull("imageUrl");
        node.put("reportedUserId", snapshot.reportedUserId());
        node.put("textScope", snapshot.textScope());
        return JSON.writeValueAsString(node);
    }
}
