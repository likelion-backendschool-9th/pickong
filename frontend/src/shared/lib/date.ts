const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** "2026-11-21" → Date (로컬 자정) */
export function parseDate(ymd: string): Date {
  const [y, m, d] = ymd.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** "2026-11-21" → "11.21 (토)" */
export function formatShortDate(ymd: string): string {
  const date = parseDate(ymd);
  return `${date.getMonth() + 1}.${date.getDate()} (${WEEKDAYS[date.getDay()]})`;
}

/** "2026-11-21" → "11월 21일 (토)" */
export function formatLongDate(ymd: string): string {
  const date = parseDate(ymd);
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAYS[date.getDay()]})`;
}

export function weekday(ymd: string): number {
  return parseDate(ymd).getDay();
}

export function weekdayLabel(day: number): string {
  return WEEKDAYS[day];
}

/** ISO 시각 → "방금 전", "2시간 전", "3일 전" */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const diff = Math.max(0, now - new Date(iso).getTime());
  const min = Math.floor(diff / 60_000);
  if (min < 1) return "방금 전";
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day < 7) return `${day}일 전`;
  const d = new Date(iso);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** 오늘 날짜 "YYYY-MM-DD" */
export function todayYmd(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** ISO 시각 → "오후 3:05" (메시지 시각) */
export function formatClock(iso: string): string {
  const d = new Date(iso);
  const h = d.getHours();
  return `${h < 12 ? "오전" : "오후"} ${h % 12 === 0 ? 12 : h % 12}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** ISO 시각 → 로컬 날짜 "YYYY-MM-DD" (같은 날 묶기용) */
export function localYmd(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
