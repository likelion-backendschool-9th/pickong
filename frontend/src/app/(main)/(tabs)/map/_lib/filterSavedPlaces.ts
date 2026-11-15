import type { PlaceCategory, SavedPlace, VisitStatus } from "@/entities/place";

export interface SavedPlaceFilter {
  query: string;
  collectionId: string | "ALL" | "NONE";
  category: PlaceCategory | "ALL";
  visit: VisitStatus | "ALL";
}

export const EMPTY_FILTER: SavedPlaceFilter = { query: "", collectionId: "ALL", category: "ALL", visit: "ALL" };

/** 내 지도와 저장 목록이 같은 조건으로 걸러 보도록 공통 함수로 둡니다. */
export function filterSavedPlaces(saved: SavedPlace[], f: SavedPlaceFilter): SavedPlace[] {
  const q = f.query.replace(/\s+/g, "").toLowerCase();
  return saved.filter((s) => {
    if (q && !(s.place.name + s.place.region + s.place.address).replace(/\s+/g, "").toLowerCase().includes(q)) return false;
    if (f.collectionId === "NONE" && s.collectionIds.length > 0) return false;
    if (f.collectionId !== "ALL" && f.collectionId !== "NONE" && !s.collectionIds.includes(f.collectionId)) return false;
    if (f.category !== "ALL" && s.place.category !== f.category) return false;
    if (f.visit !== "ALL" && s.visitStatus !== f.visit) return false;
    return true;
  });
}
