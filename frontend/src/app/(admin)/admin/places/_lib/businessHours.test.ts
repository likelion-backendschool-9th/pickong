import { describe, expect, it } from "vitest";
import {
  formatAdminBusinessHours,
  formatMinuteOfDay,
  isHoursUnregistered,
  MINUTES_PER_DAY,
  parseClock,
  parseCloseClock,
  validateBusinessHours,
  validateHoursExceptions,
} from "./businessHours";
import type { AdminPlaceExceptionInput, AdminPlaceHourInput } from "./models";

const open = (dayOfWeek: number, openTime: number, closeTime: number): AdminPlaceHourInput => ({
  dayOfWeek, closed: false, openTime, closeTime,
});
const closedDay = (dayOfWeek: number): AdminPlaceHourInput => ({ dayOfWeek, closed: true, openTime: 540, closeTime: 100 });
const fields = (rows: AdminPlaceHourInput[]) => validateBusinessHours(rows).map((item) => item.field);

describe("영업시간 표시", () => {
  it("분과 시각을 서로 바꿉니다", () => {
    expect(parseClock("09:00")).toBe(540);
    expect(parseClock("22:00")).toBe(1320);
    expect(parseClock("24:00")).toBe(MINUTES_PER_DAY);
    expect(parseCloseClock("02:00", true)).toBe(1560);
    expect(formatMinuteOfDay(540)).toBe("09:00");
    expect(formatMinuteOfDay(1440)).toBe("24:00");
    expect(formatMinuteOfDay(1560)).toBe("익일 02:00");
  });

  it("휴무·미등록·24시간을 구분합니다", () => {
    expect(formatAdminBusinessHours({ closed: true, openTime: 0, closeTime: 0 })).toBe("휴무");
    expect(formatAdminBusinessHours({ closed: false, openTime: 0, closeTime: 1440 })).toBe("24시간");
    expect(formatAdminBusinessHours({ closed: false, openTime: 1320, closeTime: 1560 })).toBe("22:00–익일 02:00");
    expect(isHoursUnregistered([open(1, 540, 1320)], 0)).toBe(true);
    expect(isHoursUnregistered([closedDay(0)], 0)).toBe(false);
  });
});

describe("요일별 영업시간 검증", () => {
  it("정상 영업, 24시간, 자정 이후 종료, 휴무, 미등록을 허용합니다", () => {
    expect(validateBusinessHours([])).toEqual([]);
    expect(validateBusinessHours([open(1, 540, 1320), closedDay(0), open(5, 0, 1440), open(6, 1320, 1560)])).toEqual([]);
  });

  it("종료가 시작보다 이르거나 0분이거나 범위를 벗어나면 거부합니다", () => {
    expect(fields([open(1, 1320, 540)])).toEqual(["businessHours.0.time"]);
    expect(fields([open(1, 540, 540)])).toEqual(["businessHours.0.time"]);
    expect(fields([open(1, 1440, 1500)])).toEqual(["businessHours.0.time"]);
    expect(fields([open(1, 1320, 1320 + MINUTES_PER_DAY + 1)])).toEqual(["businessHours.0.time"]);
    expect(fields([open(1, Number.NaN, 1320)])).toEqual(["businessHours.0.time"]);
  });

  it("휴무 행의 잘못된 시각은 영업시간으로 보지 않습니다", () => {
    expect(validateBusinessHours([closedDay(2)])).toEqual([]);
  });

  it("요일 중복과 범위 밖 요일을 거부합니다", () => {
    expect(fields([open(1, 540, 1320), open(1, 600, 700)])).toEqual(["businessHours.1.dayOfWeek"]);
    expect(fields([{ dayOfWeek: 7, closed: false, openTime: 0, closeTime: 60 }])).toEqual(["businessHours.0.dayOfWeek"]);
    expect(fields([{ dayOfWeek: null, closed: true, openTime: null, closeTime: null }])).toEqual(["businessHours.0.dayOfWeek"]);
  });
});

describe("예외 영업시간 검증", () => {
  const holiday = (exceptionDate: string, patch: Partial<AdminPlaceExceptionInput> = {}): AdminPlaceExceptionInput => ({
    exceptionDate, closed: false, openTime: 600, closeTime: 900, note: "", ...patch,
  });

  it("예외 영업과 예외 휴무를 허용합니다", () => {
    expect(validateHoursExceptions([
      holiday("2026-12-24"),
      holiday("2026-12-25", { closed: true, openTime: null, closeTime: null, note: "임시 휴무" }),
    ])).toEqual([]);
  });

  it("중복 날짜, 없는 날짜, 휴무에 붙은 시간, 잘못된 구간을 거부합니다", () => {
    const issues = validateHoursExceptions([
      holiday("2026-02-31"),
      holiday("2026-12-25", { closed: true, openTime: 540, closeTime: 600 }),
      holiday("2026-12-25"),
      holiday("2026-01-01", { openTime: 900, closeTime: 600 }),
    ]);
    expect(issues.map((item) => item.field)).toEqual([
      "hoursExceptions.0.exceptionDate",
      "hoursExceptions.1.time",
      "hoursExceptions.2.exceptionDate",
      "hoursExceptions.3.time",
    ]);
  });

  it("메모 길이 제한이 스키마에 없어 긴 메모를 자르지 않습니다", () => {
    expect(validateHoursExceptions([holiday("2026-03-01", { note: "가".repeat(1000) })])).toEqual([]);
  });
});
