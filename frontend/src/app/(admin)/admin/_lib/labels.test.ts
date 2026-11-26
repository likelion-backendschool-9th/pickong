import { describe, expect, it } from "vitest";
import { reportReasonLabel, sanctionSummary } from "./labels";

describe("sanctionSummary", () => {
  it("기간이 있는 제재는 끝나는 날짜를 붙인다", () => {
    const until = new Date(2026, 10, 27, 15).toISOString();
    expect(sanctionSummary({ type: "SUSPEND_7", until })).toBe("7일 정지 · 11.27까지");
  });

  it("경고 · 영구 정지는 이름만", () => {
    expect(sanctionSummary({ type: "WARNING", until: null })).toBe("경고");
    expect(sanctionSummary({ type: "BAN", until: null })).toBe("영구 정지");
  });
});

describe("reportReasonLabel", () => {
  it("모르는 사유는 그대로", () => {
    expect(reportReasonLabel("SPAM")).toBe("스팸 · 홍보");
    expect(reportReasonLabel("NEW_REASON")).toBe("NEW_REASON");
  });
});
