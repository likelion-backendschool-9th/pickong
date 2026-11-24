import { db, findPlace, ME_ID, nextId, placeDto, savedPlaceDto } from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";

/* 개인 태그 · 장소별 개인 사진 · 직접 등록 장소 관리 (mock 메모리 상태) */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();
const CATEGORIES = ["CAFE", "RESTAURANT", "PHOTO_SPOT", "SHOP", "EXHIBITION", "STREET", "PARK", "ATTRACTION", "STATION", "ETC"];
const VISIBILITIES = ["PUBLIC", "FOLLOWERS", "PRIVATE"];

const mySaved = () => db.savedPlaces.filter((s) => s.ownerId === ME_ID);
const mySavedOr400 = (placeId: string) => {
  const saved = mySaved().find((s) => s.placeId === placeId);
  if (!saved) throw new HttpError(400, "NOT_SAVED", "찜콩한 장소에만 사진과 태그를 남길 수 있어요.");
  return saved;
};
const myCustomPlaceOr404 = (id: string) => {
  const place = findPlace(id);
  if (!place || !place.isCustom || place.ownerId !== ME_ID) return notFound("직접 등록한 장소");
  return place;
};

/** 저장한 장소별 개인 태그 (placeId → 태그). 나만 보는 분류입니다. */
const tagsByPlace: Record<string, string[]> = {
  "p-onyu": ["조용한", "작업하기좋은", "디저트"],
  "p-salt": ["디저트", "포장"],
  "p-gyeol": ["전시", "비오는날"],
  "p-yeonmu": ["산책", "사진"],
  "p-hanok": ["사진", "산책", "외국인친구"],
  "p-book": ["조용한", "비오는날"],
  "p-baram": ["작업하기좋은"],
  "p-my-alley": ["사진", "노을"],
  "p-my-bench": ["노을", "산책"],
};

/** 장소별 개인 사진. 업로드 API 전까지 새로 추가한 사진은 브라우저 임시 URL입니다. */
let photos = [
  { id: "ph-1", placeId: "p-onyu", url: "/images/photos/cafe.jpg", createdAt: ago(5) },
  { id: "ph-2", placeId: "p-onyu", url: "/images/photos/shop.jpg", createdAt: ago(2) },
  { id: "ph-3", placeId: "p-yeonmu", url: "/images/photos/street.jpg", createdAt: ago(9) },
];

const normTag = (t: string) => t.replace(/^#/, "").replace(/\s+/g, "").trim();

function tagSummary() {
  const saved = new Set(mySaved().map((s) => s.placeId));
  const counts = new Map<string, number>();
  for (const [placeId, tags] of Object.entries(tagsByPlace)) {
    if (!saved.has(placeId)) continue;
    for (const t of tags) counts.set(t, (counts.get(t) ?? 0) + 1);
  }
  return [...counts].map(([name, placeCount]) => ({ name, placeCount }));
}

export const myPlaceRoutes: Route[] = [
  // ── 개인 태그 ──────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/me/tags",
    handler: ({ query }) => {
      const q = normTag(query.get("q") ?? "").toLowerCase();
      return tagSummary()
        .filter((t) => !q || t.name.toLowerCase().includes(q))
        .sort((a, b) => b.placeCount - a.placeCount || a.name.localeCompare(b.name));
    },
  },
  {
    method: "GET",
    path: "/api/v1/me/tags/:name/places",
    handler: ({ params }) => {
      const name = normTag(decodeURIComponent(params.name));
      const places = mySaved().filter((s) => tagsByPlace[s.placeId]?.includes(name));
      return { name, places: places.map((s) => ({ ...savedPlaceDto(s), tags: tagsByPlace[s.placeId] ?? [] })) };
    },
  },
  { method: "GET", path: "/api/v1/me/places/:id/tags", handler: ({ params }) => tagsByPlace[params.id] ?? [] },
  {
    method: "PUT",
    path: "/api/v1/me/places/:id/tags",
    handler: ({ params, body }) => {
      mySavedOr400(params.id);
      const list: unknown = body?.tags;
      if (!Array.isArray(list)) throw new HttpError(400, "INVALID_INPUT", "태그 형식을 확인해 주세요.");
      const tags = [...new Set(list.map((t) => normTag(String(t))).filter(Boolean))];
      if (tags.some((t) => t.length > 15)) throw new HttpError(400, "INVALID_INPUT", "태그는 15자까지 쓸 수 있어요.");
      if (tags.length > 10) throw new HttpError(400, "INVALID_INPUT", "태그는 장소마다 10개까지 달 수 있어요.");
      tagsByPlace[params.id] = tags;
      return tags;
    },
  },

  // ── 장소별 개인 사진 ────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/me/places/:id/photos",
    handler: ({ params }) =>
      photos.filter((p) => p.placeId === params.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
  },
  {
    method: "POST",
    path: "/api/v1/me/places/:id/photos",
    delay: 400,
    handler: ({ params, body }) => {
      mySavedOr400(params.id);
      const urls: unknown = body?.urls;
      if (!Array.isArray(urls) || urls.length === 0) throw new HttpError(400, "INVALID_INPUT", "추가할 사진을 골라 주세요.");
      if (photos.filter((p) => p.placeId === params.id).length + urls.length > 20) {
        throw new HttpError(400, "TOO_MANY_PHOTOS", "장소마다 사진은 20장까지 남길 수 있어요.");
      }
      const created = urls.map((url) => ({ id: nextId("ph"), placeId: params.id, url: String(url), createdAt: new Date().toISOString() }));
      photos = [...created, ...photos];
      return created;
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/me/places/:id/photos",
    handler: ({ params, body }) => {
      const ids: unknown = body?.photoIds;
      if (!Array.isArray(ids)) throw new HttpError(400, "INVALID_INPUT", "삭제할 사진을 골라 주세요.");
      photos = photos.filter((p) => !(p.placeId === params.id && ids.includes(p.id)));
      return undefined;
    },
  },

  // ── 직접 등록 장소 관리 ─────────────────────────────
  {
    method: "GET",
    path: "/api/v1/me/places",
    handler: () =>
      db.places
        .filter((p) => p.isCustom && p.ownerId === ME_ID)
        .map((p) => ({ ...placeDto(p), photoCount: photos.filter((ph) => ph.placeId === p.id).length })),
  },
  {
    method: "PATCH",
    path: "/api/v1/places/:id",
    delay: 300,
    handler: ({ params, body }) => {
      const place = myCustomPlaceOr404(params.id);
      if (body?.name !== undefined) {
        const name = String(body.name).trim();
        if (!name) throw new HttpError(400, "INVALID_INPUT", "장소 이름을 입력해 주세요.");
        if (name.length > 30) throw new HttpError(400, "INVALID_INPUT", "장소 이름은 30자까지 쓸 수 있어요.");
        place.name = name;
      }
      if (body?.category !== undefined) {
        if (!CATEGORIES.includes(body.category)) throw new HttpError(400, "INVALID_INPUT", "카테고리를 확인해 주세요.");
        place.category = body.category;
      }
      if (body?.visibility !== undefined) {
        if (!VISIBILITIES.includes(body.visibility)) throw new HttpError(400, "INVALID_INPUT", "공개 범위를 확인해 주세요.");
        place.visibility = body.visibility;
      }
      if (body?.description !== undefined) place.description = String(body.description).slice(0, 200);
      return placeDto(place);
    },
  },
  {
    /** 직접 등록한 장소 삭제: 내 지도 저장 · 개인 사진 · 태그도 함께 지웁니다. */
    method: "DELETE",
    path: "/api/v1/places/:id",
    handler: ({ params }) => {
      const place = myCustomPlaceOr404(params.id);
      db.places = db.places.filter((p) => p.id !== place.id);
      db.savedPlaces = db.savedPlaces.filter((s) => s.placeId !== place.id);
      photos = photos.filter((p) => p.placeId !== place.id);
      delete tagsByPlace[place.id];
      return undefined;
    },
  },
];
