import { describe, expect, it } from "vitest";
import type { PendingTerms } from "../_api/consentApi";
import { canSubmit, hasRequired, safeNext, toggleAll } from "./consent";

const privacy: PendingTerms = { type: "PRIVACY", title: "개인정보 수집 및 이용", required: true, version: "1.1" };
const marketing: PendingTerms = { type: "MARKETING", title: "마케팅 정보 수신", required: false, version: "1.1" };

describe("canSubmit", () => {
  it("필수 약관에 체크해야 넘어갈 수 있다", () => {
    expect(canSubmit([privacy, marketing], new Set())).toBe(false);
    expect(canSubmit([privacy, marketing], new Set(["MARKETING"]))).toBe(false);
    expect(canSubmit([privacy, marketing], new Set(["PRIVACY"]))).toBe(true);
  });

  it("선택 약관만 남았으면 체크하지 않아도 넘어갈 수 있다", () => {
    expect(canSubmit([marketing], new Set())).toBe(true);
  });
});

describe("hasRequired", () => {
  it("필수 약관이 하나라도 있으면 true", () => {
    expect(hasRequired([privacy, marketing])).toBe(true);
    expect(hasRequired([marketing])).toBe(false);
  });
});

describe("toggleAll", () => {
  it("하나라도 빠져 있으면 모두 체크하고, 모두 체크돼 있으면 모두 해제한다", () => {
    expect(toggleAll([privacy, marketing], new Set(["PRIVACY"]))).toEqual(new Set(["PRIVACY", "MARKETING"]));
    expect(toggleAll([privacy, marketing], new Set(["PRIVACY", "MARKETING"]))).toEqual(new Set());
  });
});

describe("safeNext", () => {
  it("찜콩 안 경로는 그대로 돌아간다 (검색어 포함)", () => {
    expect(safeNext("/map?tab=saved")).toBe("/map?tab=saved");
  });

  it("없거나 다른 사이트 주소면 피드로 보낸다", () => {
    for (const next of [undefined, null, "", "https://evil.example", "//evil.example", "/\\evil.example", "feed"]) {
      expect(safeNext(next)).toBe("/feed");
    }
  });

  it("재동의 화면 자신이면 피드로 보낸다 (같은 화면을 다시 열지 않게)", () => {
    expect(safeNext("/terms/consent")).toBe("/feed");
    expect(safeNext("/terms/consent?next=%2Fmap")).toBe("/feed");
  });
});
