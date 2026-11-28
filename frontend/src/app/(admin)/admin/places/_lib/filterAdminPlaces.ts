import { ALL_CATEGORIES, categoryLabel, type PlaceCategory } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { ADMIN_PLACE_SOURCE_TYPES, type AdminPlaceSourceType, type AdminPlaceSummary } from "./models";

export type AdminPlaceSourceFilter = "ALL" | AdminPlaceSourceType | "OTHER";
export type AdminPlaceCategoryFilter = PlaceCategory | "ALL";

export interface AdminPlaceListQuery {
  q: string;
  category: AdminPlaceCategoryFilter;
  source: AdminPlaceSourceFilter;
}

const SOURCE_FILTERS = new Set<AdminPlaceSourceFilter>(["ALL", ...ADMIN_PLACE_SOURCE_TYPES, "OTHER"]);
const SOURCE_LABEL: Record<AdminPlaceSourceType, string> = {
  SYSTEM: "시스템",
  CUSTOM: "사용자 등록",
  AI_EXTRACTED: "AI 추출",
};

export function isKnownAdminPlaceSource(sourceType: string): sourceType is AdminPlaceSourceType {
  return (ADMIN_PLACE_SOURCE_TYPES as readonly string[]).includes(sourceType);
}

export function adminPlaceSourceText(sourceType: string): string {
  return isKnownAdminPlaceSource(sourceType) ? SOURCE_LABEL[sourceType] : "기타 출처";
}

/** 알려진 카테고리만 한글 라벨을 씁니다. 알 수 없는 값은 ETC로 바꾸지 않고 받은 문자열을 그대로 보여 줍니다. */
export function adminPlaceCategoryText(category: string | null): string {
  if (!category) return "카테고리 없음";
  if ((ALL_CATEGORIES as readonly string[]).includes(category)) return categoryLabel(category as PlaceCategory);
  return category;
}

export function adminPlaceImageState(imageUrl: string | null, failed: boolean): "empty" | "failed" | "show" {
  if (!imageUrl?.trim()) return "empty";
  return failed ? "failed" : "show";
}

export function parseAdminPlaceQuery(params: Pick<URLSearchParams, "get">): AdminPlaceListQuery {
  const category = params.get("category") ?? "ALL";
  const source = params.get("source") ?? "ALL";
  return {
    q: params.get("q") ?? "",
    category: category !== "ALL" && (ALL_CATEGORIES as readonly string[]).includes(category) ? (category as PlaceCategory) : "ALL",
    source: SOURCE_FILTERS.has(source as AdminPlaceSourceFilter) ? (source as AdminPlaceSourceFilter) : "ALL",
  };
}

export function adminPlaceSearchParams(query: AdminPlaceListQuery): string {
  const params = new URLSearchParams();
  const q = query.q.trim();
  if (q) params.set("q", q);
  if (query.category !== "ALL") params.set("category", query.category);
  if (query.source !== "ALL") params.set("source", query.source);
  return params.toString();
}

/** 상세 주소에 실린 목록 조건에서 q, category, source만 남깁니다. */
export function adminPlaceReturnQuery(raw: string | null): string {
  if (!raw) return "";
  return adminPlaceSearchParams(parseAdminPlaceQuery(new URLSearchParams(raw)));
}

export function adminPlaceDetailHref(id: string, listQuery = "", edit = false): string {
  const params = new URLSearchParams();
  const list = adminPlaceReturnQuery(listQuery);
  if (list) params.set("list", list);
  if (edit) params.set("mode", "edit");
  const qs = params.toString();
  const path = routes.adminPlace(id);
  return qs ? `${path}?${qs}` : path;
}

export function adminPlaceListHref(params: Pick<URLSearchParams, "get">): string {
  const qs = adminPlaceReturnQuery(params.get("list"));
  return qs ? `${routes.adminPlaces()}?${qs}` : routes.adminPlaces();
}

export function hasAdminPlaceFilter(query: AdminPlaceListQuery): boolean {
  return query.q.trim() !== "" || query.category !== "ALL" || query.source !== "ALL";
}

/** 이름 또는 주소가 검색어를 포함하고, 카테고리와 출처는 모두 맞아야 합니다. 배열 순서는 유지합니다. */
export function filterAdminPlaces(items: readonly AdminPlaceSummary[], query: AdminPlaceListQuery): AdminPlaceSummary[] {
  const q = query.q.trim().toLowerCase();
  return items.filter((item) => {
    if (q) {
      const name = item.name.toLowerCase().includes(q);
      const address = item.address?.toLowerCase().includes(q) ?? false;
      if (!name && !address) return false;
    }
    if (query.category !== "ALL" && item.category !== query.category) return false;
    if (query.source === "OTHER") return !isKnownAdminPlaceSource(item.sourceType);
    if (query.source !== "ALL" && item.sourceType !== query.source) return false;
    return true;
  });
}
