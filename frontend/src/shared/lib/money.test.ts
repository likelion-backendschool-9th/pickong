import { describe, expect, it } from "vitest";
import { formatWon } from "./money";

describe("formatWon", () => {
  it("천 단위 쉼표와 ‘원’을 붙인다", () => {
    expect(formatWon(1900)).toBe("1,900원");
    expect(formatWon(500)).toBe("500원");
    expect(formatWon(50000)).toBe("50,000원");
  });
});
