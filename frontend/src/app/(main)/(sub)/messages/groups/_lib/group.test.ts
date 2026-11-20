import { describe, expect, it } from "vitest";
import { groupNameError, remainingSeats, toggleId } from "./group";

describe("groupNameError", () => {
  it("공백만 있거나 20자를 넘으면 오류", () => {
    expect(groupNameError("  ")).not.toBeNull();
    expect(groupNameError("가".repeat(21))).not.toBeNull();
    expect(groupNameError(" 성수 카페 투어 ")).toBeNull();
  });
});

describe("toggleId", () => {
  it("있으면 빼고 없으면 넣는다", () => {
    expect(toggleId(["a", "b"], "a")).toEqual(["b"]);
    expect(toggleId(["a"], "b")).toEqual(["a", "b"]);
  });
});

describe("remainingSeats", () => {
  it("30명에서 지금 인원을 뺀다", () => {
    expect(remainingSeats(4)).toBe(26);
    expect(remainingSeats(31)).toBe(0);
  });
});
