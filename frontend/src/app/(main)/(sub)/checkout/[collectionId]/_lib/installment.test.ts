import { describe, expect, it } from "vitest";
import { installmentLabel, installmentOptions } from "./installment";

describe("installmentOptions", () => {
  it("5만 원 미만은 일시불만", () => {
    expect(installmentOptions(1900)).toEqual([0]);
    expect(installmentOptions(49_999)).toEqual([0]);
  });

  it("5만 원부터 할부를 고를 수 있다", () => {
    expect(installmentOptions(50_000)).toEqual([0, 2, 3, 6]);
  });
});

describe("installmentLabel", () => {
  it("0은 일시불", () => {
    expect(installmentLabel(0)).toBe("일시불");
    expect(installmentLabel(3)).toBe("3개월");
  });
});
