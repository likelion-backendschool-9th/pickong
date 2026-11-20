import { describe, expect, it } from "vitest";
import { refundDaysLeft, refundReasonLabel } from "./refund";

describe("refundDaysLeft", () => {
  const now = new Date("2026-11-20T12:00:00+09:00").getTime();

  it("남은 시간을 날 단위로 올림한다", () => {
    expect(refundDaysLeft("2026-11-27T12:00:00+09:00", now)).toBe(7);
    expect(refundDaysLeft("2026-11-20T13:00:00+09:00", now)).toBe(1);
  });

  it("마감이 지났으면 0", () => {
    expect(refundDaysLeft("2026-11-20T12:00:00+09:00", now)).toBe(0);
    expect(refundDaysLeft("2026-11-19T00:00:00+09:00", now)).toBe(0);
  });
});

describe("refundReasonLabel", () => {
  it("모르는 값은 그대로 보여 준다", () => {
    expect(refundReasonLabel("WRONG_PURCHASE")).toBe("실수로 잘못 샀어요");
    expect(refundReasonLabel("SOMETHING")).toBe("SOMETHING");
  });
});
