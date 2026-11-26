/**
 * 지도 범위의 장소를 묶는 규칙. 백엔드 PlaceQueryService · MapGridPolicy · PlaceMapQueryAdapter와 같게 맞춥니다.
 *
 * 1. 같은 좌표끼리 묶습니다. 좌표 수가 MAX_SPOTS 이하이면 그대로 돌려줍니다.
 * 2. 넘으면 화면을 격자(메르카토르 기준 정사각형)로 나눠 칸마다 묶습니다.
 * 3. 한 곳만 있는 묶음은 장소로, 여러 곳이면 묶음(개수 · 평균 위치 · 범위)으로 돌려줍니다.
 */

/** 화면에 그대로 표시할 최대 좌표 수. 넘으면 격자로 묶습니다. */
export const MAX_SPOTS = 200;

/** 묶지 않은 목록(cluster=false)의 최대 장소 수 */
export const MAX_LIST = 500;

/** 긴 변을 나누는 최대 칸 수. 2의 거듭제곱으로 올리므로 실제로는 이 값의 절반 ~ 이 값입니다. */
const MAX_CELLS_PER_SIDE = 10;
const MAX_LATITUDE = 85.05;

export interface Bounds {
  west: number;
  south: number;
  east: number;
  north: number;
}

export interface MapPoint {
  lat: number;
  lng: number;
}

export interface MapCluster {
  lat: number;
  lng: number;
  count: number;
  bounds: Bounds;
}

const mercatorLatitude = (latitude: number) => {
  const clamped = Math.min(Math.max(latitude, -MAX_LATITUDE), MAX_LATITUDE);
  return (Math.log(Math.tan(((45 + clamped / 2) * Math.PI) / 180)) * 180) / Math.PI;
};

/** 범위에 맞는 격자 한 칸의 크기(도). 2의 거듭제곱이라 같은 축척에서 지도를 옮겨도 묶음이 바뀌지 않습니다. */
export function cellSize({ west, south, east, north }: Bounds): number {
  const width = east - west;
  const height = mercatorLatitude(north) - mercatorLatitude(south);
  const cell = Math.max(Math.max(width, height) / MAX_CELLS_PER_SIDE, Number.MIN_VALUE);
  return 2 ** Math.ceil(Math.log2(cell));
}

export const inBounds = (p: MapPoint, b: Bounds) =>
  p.lat >= b.south && p.lat <= b.north && p.lng >= b.west && p.lng <= b.east;

function groupBy<T extends MapPoint>(points: T[], key: (p: T) => string): T[][] {
  const groups = new Map<string, T[]>();
  for (const p of points) {
    const k = key(p);
    const group = groups.get(k);
    if (group) group.push(p);
    else groups.set(k, [p]);
  }
  return [...groups.values()];
}

function toCluster(group: MapPoint[]): MapCluster {
  let lat = 0;
  let lng = 0;
  const bounds = { west: Infinity, south: Infinity, east: -Infinity, north: -Infinity };
  for (const p of group) {
    lat += p.lat;
    lng += p.lng;
    bounds.west = Math.min(bounds.west, p.lng);
    bounds.south = Math.min(bounds.south, p.lat);
    bounds.east = Math.max(bounds.east, p.lng);
    bounds.north = Math.max(bounds.north, p.lat);
  }
  return { lat: lat / group.length, lng: lng / group.length, count: group.length, bounds };
}

/** 범위 안 장소(이미 범위 · 카테고리로 거른 목록)를 혼자 있는 장소와 묶음으로 나눕니다. */
export function clusterPlaces<T extends MapPoint>(points: T[], bounds: Bounds): { places: T[]; clusters: MapCluster[] } {
  let groups = groupBy(points, (p) => `${p.lng},${p.lat}`);
  if (groups.length > MAX_SPOTS) {
    const cell = cellSize(bounds);
    groups = groupBy(points, (p) => `${Math.floor(p.lng / cell)},${Math.floor(mercatorLatitude(p.lat) / cell)}`);
  }
  const places: T[] = [];
  const clusters: MapCluster[] = [];
  for (const group of groups) {
    if (group.length === 1) places.push(group[0]);
    else clusters.push(toCluster(group));
  }
  return { places, clusters };
}

/** 묶지 않은 목록. 백엔드처럼 이름순으로 MAX_LIST곳까지 */
export function listPlaces<T extends MapPoint & { id: string; name: string }>(points: T[]): T[] {
  return [...points]
    .sort((a, b) => a.name.localeCompare(b.name, "ko") || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))
    .slice(0, MAX_LIST);
}
