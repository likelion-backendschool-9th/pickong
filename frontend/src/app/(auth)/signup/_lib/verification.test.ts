import { describe, expect, it } from "vitest";
import { formatCountdown, normalizeCode } from "./verification";

describe("normalizeCode", () => {
  it("숫자 6자리까지만 남긴다", () => {
    expect(normalizeCode("12a 34567")).toBe("123456");
  });
});

describe("formatCountdown", () => {
  it("mm:ss로 표시하고 음수는 0으로 맞춘다", () => {
    expect(formatCountdown(180)).toBe("03:00");
    expect(formatCountdown(65)).toBe("01:05");
    expect(formatCountdown(-3)).toBe("00:00");
  });
});
