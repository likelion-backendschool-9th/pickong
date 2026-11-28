import { describe, expect, it } from "vitest";
import { dayDraftsFromInputs, emptyDayHourDrafts, exceptionDraftsFromInputs, toAdminExceptionInputs, toAdminHourInputs, type DayHourDraft, type ExceptionHourDraft } from "./hoursDraft";
import { validateBusinessHours, validateHoursExceptions } from "./businessHours";

const day = (patch: Partial<DayHourDraft> & Pick<DayHourDraft, "dayOfWeek" | "status">): DayHourDraft => ({
  open: "09:00",
  close: "18:00",
  overnight: false,
  ...patch,
});

describe("관리자 영업시간 입력 변환", () => {
  it("월요일부터 보여 주고 미등록 요일은 요청에서 뺀다", () => {
    const drafts = emptyDayHourDrafts();
    expect(drafts.map((item) => item.dayOfWeek)).toEqual([1, 2, 3, 4, 5, 6, 0]);
    expect(toAdminHourInputs(drafts)).toEqual([]);
  });

  it("영업, 휴무, 24시간, 익일 종료를 분 단위로 구분한다", () => {
    const rows = toAdminHourInputs([
      day({ dayOfWeek: 1, status: "open", open: "09:00", close: "22:00" }),
      day({ dayOfWeek: 0, status: "closed", open: "09:00", close: "18:00" }),
      day({ dayOfWeek: 2, status: "allDay" }),
      day({ dayOfWeek: 5, status: "open", open: "18:00", close: "02:00", overnight: true }),
    ]);
    expect(rows).toEqual([
      { dayOfWeek: 1, closed: false, openTime: 540, closeTime: 1320 },
      { dayOfWeek: 0, closed: true, openTime: 0, closeTime: 0 },
      { dayOfWeek: 2, closed: false, openTime: 0, closeTime: 1440 },
      { dayOfWeek: 5, closed: false, openTime: 1080, closeTime: 1560 },
    ]);
    expect(validateBusinessHours(rows)).toEqual([]);
  });

  it("익일 종료를 고르지 않은 이른 종료와 같은 시각은 통과시키지 않는다", () => {
    const early = toAdminHourInputs([day({ dayOfWeek: 5, status: "open", open: "18:00", close: "02:00", overnight: false })]);
    const same = toAdminHourInputs([day({ dayOfWeek: 1, status: "open", open: "09:00", close: "09:00" })]);
    const tooLong = toAdminHourInputs([day({ dayOfWeek: 1, status: "open", open: "00:00", close: "00:01", overnight: true })]);
    expect(validateBusinessHours(early).length).toBeGreaterThan(0);
    expect(validateBusinessHours(same).length).toBeGreaterThan(0);
    expect(validateBusinessHours(tooLong).length).toBeGreaterThan(0);
  });

  it("상태를 바꾸면 이전 영업 구간을 남기지 않는다", () => {
    const closed = toAdminHourInputs([day({ dayOfWeek: 1, status: "closed" })]);
    expect(closed).toEqual([{ dayOfWeek: 1, closed: true, openTime: 0, closeTime: 0 }]);
    expect(toAdminHourInputs([day({ dayOfWeek: 1, status: "unset", open: "09:00", close: "22:00" })])).toEqual([]);
  });

  it("예외 휴무는 null, 예외 영업과 날짜 오류를 구분한다", () => {
    const rows: ExceptionHourDraft[] = [
      { exceptionDate: "2026-12-25", status: "closed", open: "09:00", close: "18:00", overnight: false, note: "크리스마스 휴무" },
      { exceptionDate: "2026-12-31", status: "open", open: "10:00", close: "18:00", overnight: false, note: "연말 단축 영업" },
      { exceptionDate: "2026-01-01", status: "allDay", open: "10:00", close: "18:00", overnight: false, note: "" },
      { exceptionDate: "2026-05-05", status: "open", open: "18:00", close: "02:00", overnight: true, note: "야간" },
    ];
    const inputs = toAdminExceptionInputs(rows);
    expect(inputs[0]).toEqual({ exceptionDate: "2026-12-25", closed: true, openTime: null, closeTime: null, note: "크리스마스 휴무" });
    expect(inputs[1]).toMatchObject({ closed: false, openTime: 600, closeTime: 1080, note: "연말 단축 영업" });
    expect(inputs[2]).toMatchObject({ openTime: 0, closeTime: 1440, closed: false });
    expect(inputs[3]).toMatchObject({ openTime: 1080, closeTime: 1560 });
    expect(validateHoursExceptions(inputs)).toEqual([]);
    expect(validateHoursExceptions(toAdminExceptionInputs([rows[0], { ...rows[0] }])).length).toBeGreaterThan(0);
    expect(validateHoursExceptions(toAdminExceptionInputs([{ ...rows[0], exceptionDate: "2026-02-31" }])).length).toBeGreaterThan(0);
    expect(toAdminExceptionInputs(rows.filter((_, index) => index !== 1))).toHaveLength(3);
    expect(exceptionDraftsFromInputs(inputs).map((item) => item.exceptionDate)).toEqual(["2026-12-25", "2026-12-31", "2026-01-01", "2026-05-05"]);
    expect(exceptionDraftsFromInputs(inputs)[0]).toMatchObject({ status: "closed", note: "크리스마스 휴무" });
    expect(exceptionDraftsFromInputs(inputs)[3]).toMatchObject({ status: "open", open: "18:00", close: "02:00", overnight: true });
  });

  it("저장한 영업시간을 요일 순서를 유지한 채 화면 값으로 되돌린다", () => {
    const rows = toAdminHourInputs([
      day({ dayOfWeek: 1, status: "open", open: "09:00", close: "22:00" }),
      day({ dayOfWeek: 0, status: "closed" }),
      day({ dayOfWeek: 5, status: "open", open: "18:00", close: "02:00", overnight: true }),
    ]);
    const drafts = dayDraftsFromInputs(rows);
    expect(drafts.map((item) => item.dayOfWeek)).toEqual([1, 2, 3, 4, 5, 6, 0]);
    expect(drafts[0]).toMatchObject({ status: "open", open: "09:00", close: "22:00", overnight: false });
    expect(drafts[1]).toMatchObject({ status: "unset" });
    expect(drafts.find((item) => item.dayOfWeek === 0)).toMatchObject({ status: "closed" });
    expect(drafts.find((item) => item.dayOfWeek === 5)).toMatchObject({ status: "open", open: "18:00", close: "02:00", overnight: true });
    expect(toAdminHourInputs(drafts)).toEqual([rows[0], rows[2], rows[1]]);
  });
});
