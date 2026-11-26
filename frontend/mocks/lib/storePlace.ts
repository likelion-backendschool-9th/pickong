/**
 * 소상공인시장진흥공단 상가(상권)정보 CSV 한 행을 mock 장소로 바꾸는 규칙.
 * 업종 코드는 저장소 루트의 place_category_mapping.md(소분류코드 기준)를 따릅니다.
 * mock에는 카페 · 음식점 전체, 소품샵에 가까운 소매 업종, 갤러리 · 화랑(EXHIBITION)만 넣습니다.
 */
import type { PlaceCategory } from "../fixtures/types.ts";

/** 빵/도넛, 아이스크림/빙수, 카페 */
const CAFE_CODES = ["I21001", "I21008", "I21201"];

/** 음식점 · 주점 (구내식당, 유흥 주점, 떡/한과는 제외) */
const RESTAURANT_CODES = [
  "I20101", "I20102", "I20103", "I20104", "I20105", "I20106", "I20107", "I20108", "I20109",
  "I20110", "I20111", "I20112", "I20113", "I20199",
  "I20201", "I20202",
  "I20301", "I20302", "I20303", "I20399",
  "I20401", "I20402", "I20403", "I20499",
  "I20501", "I20599", "I20601",
  "I20702",
  "I21003", "I21004", "I21005", "I21006", "I21007", "I21099",
  "I21103", "I21104",
];

/**
 * 소품샵에 가까운 소매 업종. 변환표의 SHOP 중 일부만 씁니다.
 * 편의점 · 약국 · 슈퍼마켓처럼 일부러 찾아가지 않는 곳은 넣지 않습니다.
 */
const SHOP_CODES = [
  "G20901", // 남성 의류
  "G20902", // 여성 의류
  "G20905", // 기타 의류
  "G20907", // 액세서리/잡화
  "G20910", // 신발
  "G20911", // 가방
  "G21302", // 문구/회화용품
  "G21802", // 기념품점
  "G21901", // 꽃집
];

/** 예술품 소매업. 갤러리 · 화랑이 대부분 이 코드로 등록되어 있어 SHOP이 아니라 EXHIBITION입니다 (변환표 6장). */
const EXHIBITION_CODES = ["G21801"];

const CATEGORY_BY_CODE = new Map<string, PlaceCategory>([
  ...CAFE_CODES.map((code) => [code, "CAFE"] as const),
  ...RESTAURANT_CODES.map((code) => [code, "RESTAURANT"] as const),
  ...SHOP_CODES.map((code) => [code, "SHOP"] as const),
  ...EXHIBITION_CODES.map((code) => [code, "EXHIBITION"] as const),
]);

/** 상권업종소분류코드 → 장소 카테고리. 넣지 않는 업종이면 null */
export const storeCategory = (code: string): PlaceCategory | null => CATEGORY_BY_CODE.get(code.trim()) ?? null;

/**
 * 행정동 이름을 화면에 보여 줄 동네 이름으로 줄입니다.
 * 성수2가3동 → 성수동, 역삼1동 → 역삼동, 잠실본동 → 잠실동, 명동 → 명동
 */
export const storeRegion = (adminDong: string): string => adminDong.trim().replace(/(\d+가)?(\d+|본)?동$/, "동");

/** storePlaces.json에 저장하는 한 행: [id, 이름, 주소, 동네, 위도, 경도, 카테고리, 업종 설명] */
export type StorePlaceTuple = [string, string, string, string, number, number, PlaceCategory, string];

/** CSV에서 쓰는 열 (헤더 이름) */
export interface StoreCsvRow {
  상가업소번호: string;
  상호명: string;
  지점명: string;
  상권업종소분류코드: string;
  상권업종소분류명: string;
  행정동명: string;
  지번주소: string;
  도로명주소: string;
  경도: string;
  위도: string;
}

/** 좌표는 소수 6자리(약 10cm)면 충분합니다. 파일 크기를 줄이려고 자릅니다. */
const roundCoord = (value: number) => Math.round(value * 1e6) / 1e6;

/** CSV 한 행을 mock 장소로 바꿉니다. 넣지 않는 업종이거나 좌표가 잘못되면 null */
export function toStorePlace(row: StoreCsvRow): StorePlaceTuple | null {
  const category = storeCategory(row.상권업종소분류코드);
  if (!category) return null;
  const lat = Number(row.위도);
  const lng = Number(row.경도);
  if (!row.위도 || !row.경도 || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  const name = [row.상호명.trim(), row.지점명.trim()].filter(Boolean).join(" ");
  if (!name) return null;
  return [
    `s-${row.상가업소번호.trim()}`,
    name,
    (row.도로명주소 || row.지번주소).trim(),
    storeRegion(row.행정동명),
    roundCoord(lat),
    roundCoord(lng),
    category,
    row.상권업종소분류명.trim(),
  ];
}

/** 따옴표로 감싼 값("a,b", "say ""hi""")도 처리하는 CSV 한 줄 파서 */
export function parseCsvLine(line: string): string[] {
  const fields: string[] = [];
  let field = "";
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        field += '"';
        i++;
      } else if (ch === '"') {
        quoted = false;
      } else {
        field += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ",") {
      fields.push(field);
      field = "";
    } else {
      field += ch;
    }
  }
  fields.push(field);
  return fields;
}
