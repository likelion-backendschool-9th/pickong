import { describe, expect, it } from "vitest";
import { compareFirstLine, compareFull, excerpt } from "./reportContentComparison";

describe("compareFull", () => {
  it("백엔드와 같이 전체 본문만 정규화해서 비교한다", () => {
    expect(compareFull("첫 줄\n둘째 줄", "첫 줄\n둘째 줄")).toBe(false);
    expect(compareFull("이전 첫 줄\n둘째 줄", "새 첫 줄\n둘째 줄")).toBe(true);
    expect(compareFull("첫 줄\n신고 당시", "첫 줄\n현재")).toBe(true);
    expect(compareFull("첫 줄\n가운데\n이전 끝", "첫 줄\n가운데\n새 끝")).toBe(true);
    expect(compareFull("첫 줄\n끝", "첫 줄\n추가된 문장\n끝")).toBe(true);
    expect(compareFull("첫 줄\n빠질 문장\n끝", "첫 줄\n끝")).toBe(true);
    expect(compareFull("첫 줄\r\n둘째 줄", "첫 줄\n둘째 줄")).toBe(false);
    expect(compareFull("첫 줄\r둘째 줄", "첫 줄\n둘째 줄")).toBe(false);
    expect(compareFull("  첫 줄\n둘째 줄  ", "첫 줄\n둘째 줄")).toBe(false);
    expect(compareFull("첫 줄\n사이  간격", "첫 줄\n사이 간격")).toBe(true);
    expect(compareFull("첫 줄\n\n둘째 줄", "첫 줄\n둘째 줄")).toBe(true);
    expect(compareFull("Hello", "hello")).toBe(true);
    expect(compareFull(null, "현재 본문")).toBeNull();
    expect(compareFull("신고 당시", null)).toBeNull();
    expect(compareFull("", "")).toBe(false);
    expect(compareFull("", "현재 본문")).toBe(true);
    expect(compareFull("신고 당시", "")).toBe(true);
    expect(compareFull("\n신고 당시", "\n현재")).toBe(true);
    expect(compareFull("본문 <b>이전</b>", "본문 <b>이후</b>")).toBe(true);
    expect(compareFull("본문 **강조**", "본문 강조")).toBe(true);
    expect(compareFull("본문 **강조**\n둘째", "본문 **강조**\n둘째")).toBe(false);
    expect(compareFull(" \r\n ", "\n")).toBe(false);
    expect(compareFull(null, "")).toBeNull();
  });

  it("Java strip과 같이 NBSP는 남기고 파일 구분 문자는 양끝에서 뺀다", () => {
    expect(compareFull("\u00A0a", "a")).toBe(true);
    expect(compareFull("\u001Ca\u001C", "a")).toBe(false);
  });

  it("전달한 문자열을 바꾸지 않는다", () => {
    const snapshot = "  첫 줄\r\n둘째  ";
    const current = "첫 줄\n둘째";
    expect(compareFull(snapshot, current)).toBe(false);
    expect(snapshot).toBe("  첫 줄\r\n둘째  ");
    expect(current).toBe("첫 줄\n둘째");
  });
});

describe("compareFirstLine", () => {
  it("하위 호환으로 둘째 줄이 달라도 첫 줄이 같으면 false다", () => {
    expect(compareFirstLine("이전 첫 줄\n같은 뒤", "새 첫 줄\n같은 뒤")).toBe(true);
    expect(compareFirstLine("첫 줄\n신고 당시", "첫 줄\n현재")).toBe(false);
    expect(compareFirstLine("\n둘째 줄만 있음", "현재 첫 줄\n둘째")).toBeNull();
    expect(compareFirstLine(null, "현재")).toBeNull();
    expect(compareFirstLine("신고 당시", null)).toBeNull();
    expect(compareFirstLine("같은 글", "같은 글")).toBe(false);
    expect(compareFirstLine("   ", "현재")).toBeNull();
    expect(compareFirstLine("같은\r\n다른 뒤", "같은\n또 다른 뒤")).toBe(false);
    expect(excerpt("  첫 줄  \n둘째")).toBe("첫 줄");
  });
});
