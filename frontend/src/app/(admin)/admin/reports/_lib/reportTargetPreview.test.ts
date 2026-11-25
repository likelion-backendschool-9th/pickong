import { describe, expect, it } from "vitest";
import { buildReportTarget, reportBodyNeedsToggle, reportChangeLabel, resolveMockVisibility, type ReportSnapshotSource } from "./reportTargetPreview";

const post = {
  targetType: "POST" as const,
  link: "/posts/post-1",
  liveFallback: "안녕하세요.",
  deletedText: "삭제된 게시물",
  unavailableText: "확인할 수 없는 게시물",
};

function preview(snapshot: ReportSnapshotSource | null, currentBody: string | null, extra?: { visibility?: "LIVE" | "HIDDEN" | "DELETED" | "UNAVAILABLE"; targetType?: "POST" | "COMMENT" | "USER" | "COLLECTION" }) {
  return buildReportTarget({
    ...post,
    targetType: extra?.targetType ?? "POST",
    visibility: extra?.visibility ?? "LIVE",
    snapshot,
    currentBody,
  });
}

describe("buildReportTarget", () => {
  it("FULL 게시물은 둘째 줄 변경을 감지하고 원문 전체를 돌려준다", () => {
    const evidence = "안녕하세요.\n부적절한 내용입니다.";
    const current = "안녕하세요.\n수정된 내용입니다.";
    const view = preview({ text: evidence, imageUrl: null, textScope: "FULL" }, current);

    expect(view.text).toBe("안녕하세요.");
    expect(view.snapshot).toEqual({ text: evidence, imageUrl: null });
    expect(view.current).toEqual({ text: current, imageUrl: null });
    expect(view.modified).toBe(true);
    expect(view.snapshot).not.toHaveProperty("textScope");
  });

  it("FULL 댓글은 마지막 줄만 달라도 true다", () => {
    const evidence = "첫 줄입니다.\n가운데입니다.\n신고 당시 마지막";
    const current = "첫 줄입니다.\n가운데입니다.\n수정된 마지막";
    const view = preview({ text: evidence, imageUrl: null, textScope: "FULL" }, current, { targetType: "COMMENT" });

    expect(view.text).toBe("첫 줄입니다.");
    expect(view.snapshot?.text).toBe(evidence);
    expect(view.current?.text).toBe(current);
    expect(view.modified).toBe(true);
  });

  it("하위 호환: textScope가 없으면 둘째 줄이 달라도 false다", () => {
    const view = preview({ text: "같은 첫 줄\n신고 당시 둘째", imageUrl: null }, "같은 첫 줄\n현재 둘째");
    expect(view.modified).toBe(false);
    expect(view.current?.text).toBe("같은 첫 줄\n현재 둘째");
    expect(view.snapshot?.text).toBe("같은 첫 줄\n신고 당시 둘째");
  });

  it("textScope가 null이거나 FULL이 아니면 첫 줄만 비교한다", () => {
    const differentTail = { text: "같은 첫 줄\n옛 줄", imageUrl: null as string | null };
    expect(preview({ ...differentTail, textScope: null }, "같은 첫 줄\n새 줄").modified).toBe(false);
    expect(preview({ ...differentTail, textScope: "PARTIAL" }, "같은 첫 줄\n새 줄").modified).toBe(false);
    expect(preview({ ...differentTail, textScope: "FULL " }, "같은 첫 줄\n새 줄").modified).toBe(false);
  });

  it("FULL 본문이 같으면 false다", () => {
    const text = "동일한 본문\n둘째 줄";
    expect(preview({ text, imageUrl: null, textScope: "FULL" }, text).modified).toBe(false);
  });

  it("빈 문자열과 누락을 구분한다", () => {
    expect(preview({ text: "", imageUrl: null, textScope: "FULL" }, "").modified).toBe(false);
    expect(preview({ text: "", imageUrl: null, textScope: "FULL" }, "내용").modified).toBe(true);
    expect(preview({ text: null, imageUrl: null, textScope: "FULL" }, "").modified).toBeNull();
    expect(preview(null, "내용").modified).toBeNull();
    expect(preview({ text: "내용", imageUrl: null, textScope: "FULL" }, null).modified).toBeNull();
  });

  it("삭제와 조회 불가는 current와 modified가 null이다", () => {
    const deleted = preview({ text: "삭제 전 본문", imageUrl: null, textScope: "FULL" }, "지운 뒤", { visibility: "DELETED" });
    expect(deleted.visibility).toBe("DELETED");
    expect(deleted.current).toBeNull();
    expect(deleted.modified).toBeNull();
    expect(deleted.link).toBeNull();
    expect(deleted.text).toBe("삭제 전 본문");

    const missing = preview(null, null, { visibility: "UNAVAILABLE" });
    expect(missing.modified).toBeNull();
    expect(missing.current).toBeNull();
    expect(missing.text).toBe("확인할 수 없는 게시물");
  });

  it("숨김 FULL 본문은 비교하고 링크는 없다", () => {
    const hidden = preview(
      { text: "숨김 첫 줄\n신고 당시", imageUrl: null, textScope: "FULL" },
      "숨김 첫 줄\n현재 본문",
      { visibility: "HIDDEN" },
    );
    expect(hidden.modified).toBe(true);
    expect(hidden.link).toBeNull();
    expect(hidden.current?.text).toBe("숨김 첫 줄\n현재 본문");
  });

  it("회원은 FULL이어도 표시 이름의 첫 줄만 비교한다", () => {
    const view = preview({ text: "walker (@walker)\n추가 설명", imageUrl: null, textScope: "FULL" }, "walker (@walker)", {
      targetType: "USER",
    });
    expect(view.modified).toBe(false);
  });

  it("컬렉션은 본문이 달라도 비교하지 않는다", () => {
    const view = preview({ text: "예전 제목", imageUrl: null, textScope: "FULL" }, "새 제목", { targetType: "COLLECTION" });
    expect(view.modified).toBeNull();
  });

  it("신고마다 스냅샷을 따로 유지하고 원문을 정규화하지 않는다", () => {
    const older = "  예전\n줄  ";
    const newer = "새 본문";
    const first = preview({ text: older, imageUrl: "/a.jpg", textScope: "FULL" }, newer);
    const second = preview({ text: newer, imageUrl: null, textScope: "FULL" }, newer);
    expect(first.snapshot?.text).toBe(older);
    expect(second.snapshot?.text).toBe(newer);
    expect(first.current?.text).toBe(newer);
  });
});

describe("resolveMockVisibility", () => {
  it("직접 지정한 숨김·삭제를 유지하고, 본문만 있는 없는 대상은 LIVE다", () => {
    expect(resolveMockVisibility("DELETED", "UNAVAILABLE", true)).toBe("DELETED");
    expect(resolveMockVisibility("HIDDEN", "UNAVAILABLE", true)).toBe("HIDDEN");
    expect(resolveMockVisibility(undefined, "UNAVAILABLE", true)).toBe("LIVE");
    expect(resolveMockVisibility(undefined, "LIVE", false)).toBe("LIVE");
  });
});

describe("report card copy", () => {
  it("게시물과 댓글은 본문 변경, 그 외는 내용 변경이다", () => {
    expect(reportChangeLabel("POST")).toBe("본문 변경 감지");
    expect(reportChangeLabel("COMMENT")).toBe("본문 변경 감지");
    expect(reportChangeLabel("USER")).toBe("내용 변경 감지");
  });

  it("두 줄을 넘거나 긴 본문만 펼친다", () => {
    expect(reportBodyNeedsToggle("안녕하세요.\n수정된 내용입니다.")).toBe(false);
    expect(reportBodyNeedsToggle("첫 줄\n둘째\n셋째")).toBe(true);
    expect(reportBodyNeedsToggle("가".repeat(81))).toBe(true);
  });
});
