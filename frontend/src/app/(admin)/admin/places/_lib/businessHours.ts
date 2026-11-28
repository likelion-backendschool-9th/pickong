import { toHHmm, toMinutes } from "../../../../../shared/lib/time.ts";
import type { AdminPlaceBusinessHours, AdminPlaceExceptionInput, AdminPlaceHourInput, AdminPlaceIssue } from "./models";

/**
 * 자정 기준 분. 1440은 24:00이고, 그보다 크면 다음날입니다.
 * Place 도메인의 BusinessHours와 같은 단위이며, 한 요일은 최대 24시간(종료 − 시작 ≤ 1440)입니다.
 * 종료가 시작보다 이른 값은 자정을 넘긴 영업으로 해석하지 않습니다. 그때는 1440을 더한 종료를 씁니다.
 */
export const MINUTES_PER_DAY = 1440;

const DAY_MIN = 0;
const DAY_MAX = 6;

export const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"] as const;

const CLOCK = /^(\d{2}):(\d{2})$/;

export function is24Hours(openTime: number, closeTime: number): boolean {
  return openTime === 0 && closeTime === MINUTES_PER_DAY;
}

/** 분 → 화면 시각. 1440은 24:00, 1560은 익일 02:00입니다. */
export function formatMinuteOfDay(minutes: number): string {
  if (minutes === MINUTES_PER_DAY) return "24:00";
  if (minutes > MINUTES_PER_DAY) return `익일 ${toHHmm(minutes - MINUTES_PER_DAY)}`;
  return toHHmm(minutes);
}

export function formatAdminBusinessHours(row: Pick<AdminPlaceBusinessHours, "closed" | "openTime" | "closeTime">): string {
  if (row.closed) return "휴무";
  if (row.openTime == null || row.closeTime == null) return "시간 확인 필요";
  if (is24Hours(row.openTime, row.closeTime)) return "24시간";
  return `${formatMinuteOfDay(row.openTime)}–${formatMinuteOfDay(row.closeTime)}`;
}

/** 해당 요일 행이 없으면 미등록입니다. 휴무 행이 있는 것과 다릅니다. */
export function isHoursUnregistered(rows: readonly { dayOfWeek: number | null }[], dayOfWeek: number): boolean {
  return !rows.some((row) => row.dayOfWeek === dayOfWeek);
}

/** "HH:mm" → 분. 24:00만 1440을 허용하고, 그 밖은 해당 요일 안의 시각입니다. */
export function parseClock(value: string): number | null {
  const match = CLOCK.exec(value);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (minute > 59 || hour > 24 || (hour === 24 && minute !== 0)) return null;
  const minutes = toMinutes(value);
  return Number.isInteger(minutes) ? minutes : null;
}

/** 종료 시각. nextDay면 1440을 더합니다. 익일 24:00(2880)까지 읽고, 구간이 24시간을 넘는지는 따로 검사합니다. */
export function parseCloseClock(value: string, nextDay: boolean): number | null {
  const clock = parseClock(value);
  if (clock == null) return null;
  return nextDay ? clock + MINUTES_PER_DAY : clock;
}

function issue(field: string, message: string): AdminPlaceIssue {
  return { field, message };
}

function integer(value: number | null): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

/** 영업 중인 한 구간의 오류. 휴무는 호출하지 않습니다. */
export function openIntervalMessage(openTime: number | null, closeTime: number | null): string | null {
  if (!integer(openTime) || !integer(closeTime)) return "영업 시작·종료 시간을 입력해 주세요.";
  if (openTime < 0 || openTime >= MINUTES_PER_DAY) return "시작 시간은 해당 요일의 0시부터 24시 전이어야 해요.";
  if (closeTime <= openTime) return "종료 시간은 시작보다 늦어야 해요. 자정을 넘기면 다음날 시각으로 입력해 주세요.";
  if (closeTime > openTime + MINUTES_PER_DAY) return "하루 영업시간은 24시간을 넘길 수 없어요.";
  return null;
}

export function validateBusinessHours(rows: readonly AdminPlaceHourInput[]): AdminPlaceIssue[] {
  const issues: AdminPlaceIssue[] = [];
  const seen = new Set<number>();
  rows.forEach((row, index) => {
    const field = `businessHours.${index}`;
    if (!integer(row.dayOfWeek) || row.dayOfWeek < DAY_MIN || row.dayOfWeek > DAY_MAX) {
      issues.push(issue(`${field}.dayOfWeek`, "요일은 일요일(0)부터 토요일(6)까지예요."));
    } else if (seen.has(row.dayOfWeek)) {
      issues.push(issue(`${field}.dayOfWeek`, "같은 요일을 두 번 등록할 수 없어요."));
    } else {
      seen.add(row.dayOfWeek);
    }
    if (row.closed) return;
    const message = openIntervalMessage(row.openTime, row.closeTime);
    if (message) issues.push(issue(`${field}.time`, message));
  });
  return issues;
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function isExceptionDate(value: string): boolean {
  const match = ISO_DATE.exec(value);
  if (!match) return false;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function validateHoursExceptions(rows: readonly AdminPlaceExceptionInput[]): AdminPlaceIssue[] {
  const issues: AdminPlaceIssue[] = [];
  const seen = new Set<string>();
  rows.forEach((row, index) => {
    const field = `hoursExceptions.${index}`;
    if (!isExceptionDate(row.exceptionDate)) {
      issues.push(issue(`${field}.exceptionDate`, "날짜를 확인해 주세요."));
    } else if (seen.has(row.exceptionDate)) {
      issues.push(issue(`${field}.exceptionDate`, "같은 날짜를 두 번 등록할 수 없어요."));
    } else {
      seen.add(row.exceptionDate);
    }
    if (row.closed) {
      if (row.openTime != null || row.closeTime != null) {
        issues.push(issue(`${field}.time`, "휴무일에는 영업시간을 넣지 않아 주세요."));
      }
      return;
    }
    const message = openIntervalMessage(row.openTime, row.closeTime);
    if (message) issues.push(issue(`${field}.time`, message));
  });
  return issues;
}
