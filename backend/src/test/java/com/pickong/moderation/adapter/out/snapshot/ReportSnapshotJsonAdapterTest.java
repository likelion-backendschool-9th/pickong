package com.pickong.moderation.adapter.out.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.moderation.application.ReportSnapshots;
import com.pickong.moderation.application.dto.ReportTargetLookup;
import com.pickong.moderation.domain.model.ReportSnapshot;
import com.pickong.moderation.domain.vo.ReportTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReportSnapshotJsonAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ReportSnapshotJsonAdapter writer = new ReportSnapshotJsonAdapter();

    @Test
    @DisplayName("게시물과 댓글의 본문 원문, 작성자 id, FULL을 JSON으로 쓴다")
    void writesFullSnapshotWithoutNormalizing() {
        String post = "  첫 줄\r\n둘째 줄  ";
        String comment = "첫 줄\n\n마지막";
        ReportSnapshot postSnapshot = ReportSnapshots.from(new ReportTargetLookup(ReportTargetType.FEED, 10L, 123L, post));
        ReportSnapshot commentSnapshot = ReportSnapshots.from(new ReportTargetLookup(ReportTargetType.COMMENT, 11L, 456L, comment));

        JsonNode postJson = JSON.readTree(writer.write(postSnapshot));
        JsonNode commentJson = JSON.readTree(writer.write(commentSnapshot));

        assertThat(postJson.get("text").asString()).isEqualTo(post);
        assertThat(postJson.get("imageUrl").isNull()).isTrue();
        assertThat(postJson.get("reportedUserId").asLong()).isEqualTo(123L);
        assertThat(postJson.get("textScope").asString()).isEqualTo("FULL");
        assertThat(commentJson.get("text").asString()).isEqualTo(comment);
        assertThat(commentJson.get("reportedUserId").asLong()).isEqualTo(456L);
        assertThat(postSnapshot.text()).isEqualTo(post);
        assertThat(commentSnapshot.text()).isEqualTo(comment);
    }

    @Test
    @DisplayName("빈 본문과 누락된 본문을 JSON에서 구분한다")
    void distinguishesBlankTextFromMissingText() {
        JsonNode blank = JSON.readTree(writer.write(ReportSnapshot.full("", 7L)));
        JsonNode missing = JSON.readTree(writer.write(ReportSnapshot.full(null, 7L)));

        assertThat(blank.get("text").isString()).isTrue();
        assertThat(blank.get("text").asString()).isEmpty();
        assertThat(missing.get("text").isNull()).isTrue();
    }

    @Test
    @DisplayName("두 스냅샷은 서로의 본문을 바꾸지 않는다")
    void snapshotsStayIndependent() {
        ReportSnapshot older = ReportSnapshot.full("예전\n본문", 1L);
        ReportSnapshot newer = ReportSnapshot.full("새 본문", 2L);

        assertThat(older.text()).isEqualTo("예전\n본문");
        assertThat(newer.text()).isEqualTo("새 본문");
        assertThat(writer.write(older)).contains("예전\\n본문");
        assertThat(writer.write(newer)).doesNotContain("예전");
    }

    @Test
    @DisplayName("회원, 컬렉션, 작성자 없음, 이미지는 전체 본문 스냅샷이 아니다")
    void rejectsTargetsThatAreNotFullPostsOrComments() {
        assertThatThrownBy(() -> ReportSnapshots.from(new ReportTargetLookup(ReportTargetType.USER, 1L, 2L, "이름")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSnapshots.from(new ReportTargetLookup(ReportTargetType.COLLECTION, 1L, 2L, "제목")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportSnapshot.full("본문", 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportSnapshot("본문", "https://example.test/a.jpg", 1L, "FULL"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
