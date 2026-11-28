import { MINUTES_PER_DAY, parseClock } from "./businessHours";
import type { AdminPlaceExceptionInput, AdminPlaceHourInput } from "./models";

/** 화면 표시 순서. 서버의 0은 일요일이고, 입력은 월요일부터 보여 줍니다. */
export const ADMIN_PLACE_DAY_ORDER = [1, 2, 3, 4, 5, 6, 0] as const;

export type DayHourStatus = "unset" | "open" | "closed" | "allDay";
export type ExceptionHourStatus = "closed" | "open" | "allDay";

export interface DayHourDraft {
  dayOfWeek: number;
  status: DayHourStatus;
  open: string;
  close: string;
  overnight: boolean;
}

export interface ExceptionHourDraft {
  exceptionDate: string;
  status: ExceptionHourStatus;
  open: string;
  close: string;
  overnight: boolean;
  note: string;
}

export function emptyDayHourDrafts(): DayHourDraft[] {
  return ADMIN_PLACE_DAY_ORDER.map((dayOfWeek) => ({ dayOfWeek, status: "unset", open: "09:00", close: "18:00", overnight: false }));
}

function openMinutes(open: string, close: string, overnight: boolean): { openTime: number | null; closeTime: number | null } {
  const openTime = parseClock(open);
  const closeClock = parseClock(close);
  if (openTime == null || closeClock == null) return { openTime, closeTime: closeClock };
  return { openTime, closeTime: closeClock + (overnight ? MINUTES_PER_DAY : 0) };
}

/** 미등록 요일은 행을 만들지 않습니다. 휴무와 24시간, 익일 종료를 구분합니다. */
export function toAdminHourInputs(days: readonly DayHourDraft[]): AdminPlaceHourInput[] {
  const rows: AdminPlaceHourInput[] = [];
  for (const day of days) {
    if (day.status === "unset") continue;
    if (day.status === "closed") {
      rows.push({ dayOfWeek: day.dayOfWeek, closed: true, openTime: 0, closeTime: 0 });
      continue;
    }
    if (day.status === "allDay") {
      rows.push({ dayOfWeek: day.dayOfWeek, closed: false, openTime: 0, closeTime: MINUTES_PER_DAY });
      continue;
    }
    const { openTime, closeTime } = openMinutes(day.open, day.close, day.overnight);
    rows.push({ dayOfWeek: day.dayOfWeek, closed: false, openTime, closeTime });
  }
  return rows;
}

function clock(minutes: number): string {
  const bounded = minutes >= MINUTES_PER_DAY ? minutes - MINUTES_PER_DAY : minutes;
  const hour = Math.floor(bounded / 60);
  const minute = bounded % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

/** 저장된 분 단위 영업시간을 화면 입력으로 되돌립니다. 수정 화면에서 초기값으로 씁니다. */
export function dayDraftsFromInputs(rows: readonly AdminPlaceHourInput[]): DayHourDraft[] {
  return ADMIN_PLACE_DAY_ORDER.map((dayOfWeek) => {
    const row = rows.find((item) => item.dayOfWeek === dayOfWeek);
    const base = { dayOfWeek, open: "09:00", close: "18:00", overnight: false };
    if (!row || row.closed) return { ...base, status: row?.closed ? "closed" : "unset" } as DayHourDraft;
    if (row.openTime === 0 && row.closeTime === MINUTES_PER_DAY) return { ...base, status: "allDay" };
    const overnight = (row.closeTime ?? 0) > MINUTES_PER_DAY;
    return {
      dayOfWeek,
      status: "open",
      open: clock(row.openTime ?? 0),
      close: clock(overnight ? (row.closeTime ?? 0) - MINUTES_PER_DAY : (row.closeTime ?? 0)),
      overnight,
    };
  });
}

/** 저장된 예외 영업시간을 화면 입력으로 되돌립니다. 날짜 문자열은 그대로 둡니다. */
export function exceptionDraftsFromInputs(rows: readonly AdminPlaceExceptionInput[]): ExceptionHourDraft[] {
  return rows.map((row) => {
    const base = { exceptionDate: row.exceptionDate, open: "10:00", close: "18:00", overnight: false, note: row.note };
    if (row.closed || row.openTime == null || row.closeTime == null) return { ...base, status: "closed" };
    if (row.openTime === 0 && row.closeTime === MINUTES_PER_DAY) return { ...base, status: "allDay" };
    const overnight = row.closeTime > MINUTES_PER_DAY;
    return {
      ...base,
      status: "open",
      open: clock(row.openTime),
      close: clock(overnight ? row.closeTime - MINUTES_PER_DAY : row.closeTime),
      overnight,
    };
  });
}

/** 예외 휴무는 시간을 null로 두고, 정기 휴무의 0, 0과 섞지 않습니다. */
export function toAdminExceptionInputs(rows: readonly ExceptionHourDraft[]): AdminPlaceExceptionInput[] {
  return rows.map((row) => {
    if (row.status === "closed") {
      return { exceptionDate: row.exceptionDate, closed: true, openTime: null, closeTime: null, note: row.note };
    }
    if (row.status === "allDay") {
      return { exceptionDate: row.exceptionDate, closed: false, openTime: 0, closeTime: MINUTES_PER_DAY, note: row.note };
    }
    const { openTime, closeTime } = openMinutes(row.open, row.close, row.overnight);
    return { exceptionDate: row.exceptionDate, closed: false, openTime, closeTime, note: row.note };
  });
}
