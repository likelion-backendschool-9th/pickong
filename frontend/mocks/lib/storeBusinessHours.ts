/**
 * mock 상가 장소에 붙이는 예시 영업시간. 실제 영업시간이 아니라 화면 확인용 값입니다.
 *
 * - 업종에 맞는 범위 안에서 30분 단위로 고릅니다.
 * - 모두 24시간 표기(HH:mm)이고 00:00 ≤ 시작 < 종료 ≤ 24:00입니다.
 *   화면의 영업 판정(entities/place/businessHours.ts)이 자정을 넘기는 영업을 다루지 않으므로 종료는 24:00까지입니다.
 * - 장소 id로 시드를 정해, mock 서버를 다시 시작해도 같은 장소는 같은 영업시간이 나옵니다.
 */
import type { BusinessHoursDto, PlaceCategory } from "../fixtures/types.ts";

/** 분 단위 범위. 30분 단위로 고릅니다. */
interface HoursRange {
  open: [number, number];
  close: [number, number];
}

const h = (hour: number) => hour * 60;

const RANGES = {
  CAFE: { open: [h(7), h(11)], close: [h(18), h(23)] },
  RESTAURANT: { open: [h(10), h(12)], close: [h(20), h(22)] },
  /** 요리 주점 · 생맥주 전문 */
  BAR: { open: [h(16), h(18)], close: [h(23), h(24)] },
  SHOP: { open: [h(10), h(12)], close: [h(19), h(22)] },
} satisfies Record<string, HoursRange>;

const BAR_DESCRIPTIONS = new Set(["요리 주점", "생맥주 전문"]);
const STEP = 30;

/** 문자열 → 32비트 시드 (FNV-1a) */
function seedOf(text: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

/** 시드로 0 이상 1 미만 값을 차례로 내는 작은 난수 생성기 (mulberry32) */
function random(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** [min, max] 안의 30분 단위 값 */
const pick = (next: () => number, [min, max]: [number, number]) =>
  min + Math.floor(next() * ((max - min) / STEP + 1)) * STEP;

/** 분 → "HH:mm" (1440분은 "24:00") */
export const toHHmm = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;

export function storeBusinessHours(id: string, category: PlaceCategory, description: string): BusinessHoursDto {
  const range =
    category === "RESTAURANT" && BAR_DESCRIPTIONS.has(description)
      ? RANGES.BAR
      : category === "CAFE" || category === "SHOP"
        ? RANGES[category]
        : RANGES.RESTAURANT;
  const next = random(seedOf(id));
  const open = pick(next, range.open);
  const close = pick(next, range.close);
  // 절반은 휴무일 없음, 나머지는 요일 하루 (0=일 ~ 6=토)
  const closedDays = next() < 0.5 ? [] : [Math.floor(next() * 7)];
  return { open: toHHmm(open), close: toHHmm(close), closedDays };
}
