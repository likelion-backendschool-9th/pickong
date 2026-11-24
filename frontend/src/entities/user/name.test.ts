import { describe, expect, it } from "vitest";
import { nameError, normalizeName, suggestedName } from "./name";

describe("이름", () => {
  it("비워 두면 null로 저장한다", () => {
    expect(normalizeName("")).toBeNull();
    expect(normalizeName("   ")).toBeNull();
    expect(normalizeName(" 김서연 ")).toBe("김서연");
  });

  it("비어 있어도 오류가 아니고, 한글 · 영문 모두 쓸 수 있다", () => {
    expect(nameError("")).toBeNull();
    expect(nameError("김서연")).toBeNull();
    expect(nameError("Seoyeon Kim")).toBeNull();
  });

  it("20자를 넘으면 오류", () => {
    expect(nameError("가".repeat(20))).toBeNull();
    expect(nameError("가".repeat(21))).not.toBeNull();
  });

  it("소셜 계정 이름을 최대 길이까지 잘라 기본값으로 쓴다", () => {
    expect(suggestedName("  콩이 ")).toBe("콩이");
    expect(suggestedName("가".repeat(25))).toBe("가".repeat(20));
    expect(suggestedName(null)).toBe("");
  });
});
