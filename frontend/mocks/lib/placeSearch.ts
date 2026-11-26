/**
 * 장소 이름 검색 순위. 실제 상가 정보가 들어가면 "카페" 같은 검색어에 수천 곳이 걸리므로
 * 이름이 검색어와 가까운 곳부터 보여 줍니다.
 */

const norm = (s: string) => s.replace(/\s+/g, "").toLowerCase();

export interface SearchablePlace {
  name: string;
  region: string;
  address: string;
}

/** 0이 가장 가깝습니다. 일치하지 않으면 null */
export function matchRank(place: SearchablePlace, query: string): number | null {
  const q = norm(query);
  if (!q) return null;
  const name = norm(place.name);
  if (name === q) return 0;
  if (name.startsWith(q)) return 1;
  if (name.includes(q)) return 2;
  if (norm(place.region).includes(q)) return 3;
  if (norm(place.address).includes(q)) return 4;
  return null;
}

/** 검색어와 가까운 순서로 limit곳까지. 순위가 같으면 원래 순서(예시 장소 먼저)를 유지합니다. */
export function searchPlaces<T extends SearchablePlace>(places: T[], query: string, limit: number): T[] {
  return places
    .map((place, index) => ({ place, index, rank: matchRank(place, query) }))
    .filter((r): r is { place: T; index: number; rank: number } => r.rank !== null)
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .slice(0, limit)
    .map((r) => r.place);
}
