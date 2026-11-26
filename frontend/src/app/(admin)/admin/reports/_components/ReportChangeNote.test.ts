import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Report } from "../../_api/adminApi";
import { ReportChangeNote } from "./ReportChangeNote";

function report(patch: Partial<Report["target"]> & { targetType?: Report["targetType"] }): Report {
  return {
    id: "rp-test",
    targetType: patch.targetType ?? "POST",
    targetId: "post-1",
    target: {
      text: "첫 줄",
      imageUrl: null,
      link: null,
      snapshot: { text: "첫 줄\n둘째 줄", imageUrl: null },
      current: { text: "첫 줄\n현재", imageUrl: null },
      modified: false,
      visibility: "LIVE",
      ...patch,
    },
    reportedUser: null,
    reporter: null,
    reason: "SPAM",
    detail: "",
    createdAt: "2026-11-25T00:00:00Z",
    status: "PENDING",
    resolution: null,
  };
}

function html(value: Report) {
  return renderToStaticMarkup(createElement(ReportChangeNote, { report: value }));
}

describe("ReportChangeNote", () => {
  it("변경된 신고는 접힌 상태에서 당시 본문 버튼과 현재 본문을 보여 준다", () => {
    const markup = html(report({ modified: true, current: { text: "첫 줄\n현재 본문", imageUrl: null } }));

    expect(markup).toContain("신고 당시 전체 본문 보기");
    expect(markup).toContain("본문 변경 감지");
    expect(markup).toContain("현재 본문");
    expect(markup).not.toContain("둘째 줄");
  });

  it("수정되지 않은 신고도 당시 본문 버튼이 있고 변경 문구는 없다", () => {
    const markup = html(report({ modified: false }));

    expect(markup).toContain("신고 당시 전체 본문 보기");
    expect(markup).not.toContain("본문 변경 감지");
    expect(markup).not.toContain("현재:");
    expect(markup).not.toContain("둘째 줄");
  });

  it("변경을 알 수 없어도 스냅샷이 있으면 당시 본문을 열 수 있다", () => {
    const markup = html(report({
      modified: null,
      visibility: "DELETED",
      current: null,
      snapshot: { text: "삭제 전 본문", imageUrl: null },
    }));

    expect(markup).toContain("신고 당시 전체 본문 보기");
    expect(markup).not.toContain("본문 변경 감지");
    expect(markup).not.toContain("삭제 전 본문");
    expect(markup).not.toContain("변경되지 않");
  });

  it("스냅샷이 없으면 버튼을 만들지 않는다", () => {
    expect(html(report({ snapshot: null, modified: null, current: null }))).toBe("");
    expect(html(report({ snapshot: { text: null, imageUrl: null }, modified: null, current: null }))).toBe("");
  });

  it("빈 스냅샷도 버튼은 두고 현재 빈 문자열은 내용 없음으로 구분한다", () => {
    const emptySnapshot = html(report({ snapshot: { text: "", imageUrl: null }, modified: false, current: null }));
    const emptyCurrent = html(report({
      modified: true,
      snapshot: { text: "당시", imageUrl: null },
      current: { text: "", imageUrl: null },
    }));

    expect(emptySnapshot).toContain("신고 당시 전체 본문 보기");
    expect(emptySnapshot).not.toContain("신고 당시 내용 없음");
    expect(emptyCurrent).toContain("현재 내용 없음");
    expect(emptyCurrent).toContain("본문 변경 감지");
  });

  it("회원 신고의 변경 문구는 내용 변경 감지다", () => {
    const markup = html(report({
      targetType: "USER",
      modified: true,
      snapshot: { text: "이전 이름", imageUrl: null },
      current: { text: "새 이름", imageUrl: null },
    }));

    expect(markup).toContain("내용 변경 감지");
  });
});
