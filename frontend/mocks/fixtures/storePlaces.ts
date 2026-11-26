import { readFileSync } from "node:fs";
import type { StorePlaceTuple } from "../lib/storePlace.ts";
import type { PlaceRow } from "./types.ts";

/**
 * 실제 상가 정보로 만든 주변 장소 (홍대 · 명동 · 잠실 · 강남 · 성수).
 * storePlaces.json은 npm run mock:stores로 CSV에서 다시 만듭니다. 직접 고치지 않습니다.
 * 상호 · 주소 · 좌표는 공공데이터 기준이라 지금과 다를 수 있고, 사진 · 영업시간은 없습니다.
 */
const file = JSON.parse(readFileSync(new URL("./storePlaces.json", import.meta.url), "utf8")) as {
  rows: StorePlaceTuple[];
};

/** 상가 정보에서 온 장소인지 (id가 s-상가업소번호) */
export const isStorePlace = (id: string) => id.startsWith("s-");

export const storePlaces: PlaceRow[] = file.rows.map(([id, name, address, region, lat, lng, category, description]) => ({
  id,
  name,
  address,
  region,
  lat,
  lng,
  category,
  isCustom: false,
  ownerId: null,
  visibility: "PUBLIC",
  description,
  imageUrl: null,
  businessHours: null,
}));
