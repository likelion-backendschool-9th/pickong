import { describe, expect, it } from "vitest";
import { addTag, normalizeTag, TAGS_PER_PLACE } from "./tags";

describe("normalizeTag", () => {
  it("앞의 #과 공백을 뺀다", () => {
    expect(normalizeTag("#작업 하기 좋은")).toBe("작업하기좋은");
    expect(normalizeTag("##노을")).toBe("노을");
  });
});

describe("addTag", () => {
  it("새 태그를 뒤에 붙인다", () => {
    expect(addTag(["사진"], "#노을")).toEqual({ tags: ["사진", "노을"], error: null });
  });

  it("빈 입력은 무시한다", () => {
    expect(addTag(["사진"], " # ")).toEqual({ tags: ["사진"], error: null });
  });

  it("중복 · 길이 · 개수 제한을 알려 준다", () => {
    expect(addTag(["사진"], "사진").error).toBe("이미 단 태그예요.");
    expect(addTag([], "가".repeat(16)).error).not.toBeNull();
    const full = Array.from({ length: TAGS_PER_PLACE }, (_, i) => `t${i}`);
    expect(addTag(full, "하나더").error).not.toBeNull();
  });
});
