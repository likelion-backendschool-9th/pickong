import { describe, expect, it } from "vitest";
import { formatClock, formatDotDate, localYmd } from "./date";

describe("formatClock", () => {
  it("오전 · 오후와 12시를 표시한다", () => {
    expect(formatClock(new Date(2026, 10, 16, 0, 5).toISOString())).toBe("오전 12:05");
    expect(formatClock(new Date(2026, 10, 16, 9, 30).toISOString())).toBe("오전 9:30");
    expect(formatClock(new Date(2026, 10, 16, 12, 0).toISOString())).toBe("오후 12:00");
    expect(formatClock(new Date(2026, 10, 16, 15, 7).toISOString())).toBe("오후 3:07");
  });
});

describe("localYmd", () => {
  it("로컬 날짜로 바꾼다", () => {
    expect(localYmd(new Date(2026, 0, 3, 23, 59).toISOString())).toBe("2026-01-03");
  });
});

describe("formatDotDate", () => {
  it("YYYY-MM-DD와 ISO 시각을 연.월.일로 보여 준다", () => {
    expect(formatDotDate("2026-11-01")).toBe("2026.11.01");
    expect(formatDotDate(new Date(2026, 0, 5, 13, 0).toISOString())).toBe("2026.01.05");
  });
});
