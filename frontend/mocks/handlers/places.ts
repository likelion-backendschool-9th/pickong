import {
  canView,
  collectionDto,
  collectionPlaceIds,
  db,
  distanceMeters,
  findPlace,
  findUser,
  ME_ID,
  nextId,
  placeDto,
  savedPlaceDto,
} from "../db.ts";
import type { SavedPlaceRow } from "../fixtures/types.ts";
import { HttpError, notFound, type Route } from "../router.ts";

const placeOr404 = (id: string) => {
  const place = findPlace(id);
  if (!place || !canView(place.ownerId ?? "", place.visibility)) return notFound("장소");
  return place;
};

const mySaved = () => db.savedPlaces.filter((s) => s.ownerId === ME_ID);

const collectionOr404 = (id: string) => {
  const c = db.collections.find((col) => col.id === id);
  if (!c || !canView(c.ownerId, c.visibility)) return notFound("컬렉션");
  return c;
};

const myCollectionOr404 = (id: string) => {
  const c = collectionOr404(id);
  if (c.ownerId !== ME_ID) throw new HttpError(403, "FORBIDDEN", "내 컬렉션만 수정할 수 있어요.");
  return c;
};

const assertMyCollections = (ids: string[]) => {
  for (const id of ids) myCollectionOr404(id);
};

export const placeRoutes: Route[] = [
  {
    /**
     * 지도 범위 안의 장소 (일반 지도처럼 주변 장소 표시용). bbox=서,남,동,북 · category(선택)
     * 실제로는 카카오 장소 데이터(카테고리 · 영역 검색)로 바뀝니다.
     */
    method: "GET",
    path: "/api/places",
    handler: ({ query }) => {
      const [west, south, east, north] = (query.get("bbox") ?? "").split(",").map(Number);
      if ([west, south, east, north].some((n) => !Number.isFinite(n))) throw new HttpError(400, "BAD_BBOX", "지도 범위를 확인해 주세요.");
      const category = query.get("category");
      return db.places
        .filter((p) => canView(p.ownerId ?? "", p.visibility))
        .filter((p) => p.lat >= south && p.lat <= north && p.lng >= west && p.lng <= east)
        .filter((p) => !category || p.category === category)
        .slice(0, 80)
        .map(placeDto);
    },
  },
  {
    method: "GET",
    path: "/api/places/search",
    delay: 250,
    handler: ({ query }) => {
      const q = (query.get("q") ?? "").trim().replace(/\s+/g, "");
      if (!q) return [];
      return db.places
        .filter((p) => p.category !== "STATION" || q.includes("역"))
        .filter((p) => canView(p.ownerId ?? "", p.visibility))
        .filter((p) => (p.name + p.region + p.address).replace(/\s+/g, "").includes(q))
        .slice(0, 20)
        .map(placeDto);
    },
  },
  {
    method: "GET",
    path: "/api/places/:id",
    handler: ({ params }) => {
      const place = placeOr404(params.id);
      const saved = mySaved().find((s) => s.placeId === place.id) ?? null;
      const sourcePost =
        (saved?.source.refId && db.posts.find((p) => p.id === saved.source.refId)) ||
        db.posts.find((p) => p.visibility === "PUBLIC" && p.photos.some((ph) => ph.placeId === place.id));
      const author = sourcePost ? findUser(sourcePost.authorId) : null;
      const visits = db.plans
        .filter((p) => p.ownerId === ME_ID && p.items.some((i) => i.placeId === place.id && i.visited))
        .map((p) => ({ planId: p.id, title: p.title, date: p.date }));
      return {
        place: placeDto(place),
        saved: saved ? savedPlaceDto(saved) : null,
        discoveredFrom:
          sourcePost && author
            ? {
                postId: sourcePost.id,
                authorNickname: author.nickname,
                quote: sourcePost.content.split("\n")[0],
                imageUrl: sourcePost.photos.find((ph) => ph.placeId === place.id)?.url ?? null,
              }
            : null,
        relatedPostCount: db.posts.filter((p) => p.photos.some((ph) => ph.placeId === place.id)).length,
        visits,
      };
    },
  },
  {
    /** 지도에서 좌표를 직접 지정한 개인 장소. 기본값은 비공개 */
    method: "POST",
    path: "/api/places",
    delay: 400,
    handler: ({ body }) => {
      const name = String(body?.name ?? "").trim();
      if (!name) throw new HttpError(400, "NO_NAME", "장소 이름을 입력해 주세요.");
      if (typeof body?.lat !== "number" || typeof body?.lng !== "number") {
        throw new HttpError(400, "NO_LOCATION", "위치를 지정해 주세요.");
      }
      const nearest = [...db.places]
        .filter((p) => !p.isCustom)
        .sort((a, b) => distanceMeters(a, body) - distanceMeters(b, body))[0];
      const place = {
        id: nextId("p"),
        name,
        address: `${nearest?.region ?? "선택한 위치"} 부근 · 직접 지정한 좌표`,
        region: nearest?.region ?? "",
        lat: body.lat,
        lng: body.lng,
        category: body.category ?? "ETC",
        isCustom: true,
        ownerId: ME_ID,
        visibility: body.visibility ?? "PRIVATE",
        description: String(body.description ?? ""),
        imageUrl: body.imageUrl ?? null,
        businessHours: null,
      };
      db.places.push(place);
      return placeDto(place);
    },
  },

  // ── 저장한 장소 (내 지도) ──────────────────────────────
  {
    method: "GET",
    path: "/api/saved-places",
    delay: 250,
    handler: () =>
      mySaved()
        .sort((a, b) => b.savedAt.localeCompare(a.savedAt))
        .map(savedPlaceDto),
  },
  {
    /**
     * 장소 찜콩. 같은 장소는 한 번만 저장하고(중복 제거),
     * 이미 저장된 장소는 새로 만들지 않고 선택한 컬렉션에만 연결합니다.
     */
    method: "POST",
    path: "/api/saved-places",
    delay: 400,
    handler: ({ body }) => {
      const placeIds: string[] = [...new Set<string>(body?.placeIds ?? [])];
      const collectionIds: string[] = body?.collectionIds ?? [];
      if (placeIds.length === 0) throw new HttpError(400, "NO_PLACE", "저장할 장소를 선택해 주세요.");
      assertMyCollections(collectionIds);

      const created: string[] = [];
      const linked: string[] = [];
      for (const placeId of placeIds) {
        placeOr404(placeId);
        const existing = mySaved().find((s) => s.placeId === placeId);
        if (existing) {
          existing.collectionIds = [...new Set([...existing.collectionIds, ...collectionIds])];
          linked.push(placeId);
        } else {
          const row: SavedPlaceRow = {
            id: nextId("sp"),
            ownerId: ME_ID,
            placeId,
            collectionIds: [...collectionIds],
            visitStatus: "WANT_TO_GO",
            memo: "",
            savedAt: new Date().toISOString(),
            source: body?.source ?? { type: "SEARCH", refId: null, label: "직접 저장" },
          };
          db.savedPlaces.push(row);
          created.push(placeId);
        }
      }
      return { createdPlaceIds: created, linkedPlaceIds: linked };
    },
  },
  {
    method: "PATCH",
    path: "/api/saved-places",
    handler: ({ body }) => {
      const ids: string[] = body?.ids ?? [];
      const targets = mySaved().filter((s) => ids.includes(s.id));
      assertMyCollections([...(body?.addCollectionIds ?? []), ...(body?.setCollectionIds ?? [])]);
      for (const s of targets) {
        if (body.visitStatus) s.visitStatus = body.visitStatus;
        if (typeof body.memo === "string") s.memo = body.memo;
        if (body.setCollectionIds) s.collectionIds = [...body.setCollectionIds];
        if (body.addCollectionIds) s.collectionIds = [...new Set([...s.collectionIds, ...body.addCollectionIds])];
        if (body.removeCollectionIds) {
          s.collectionIds = s.collectionIds.filter((c) => !body.removeCollectionIds.includes(c));
        }
      }
      return targets.map(savedPlaceDto);
    },
  },
  {
    method: "DELETE",
    path: "/api/saved-places",
    handler: ({ body }) => {
      const ids: string[] = body?.ids ?? [];
      db.savedPlaces = db.savedPlaces.filter((s) => !(s.ownerId === ME_ID && ids.includes(s.id)));
      return undefined;
    },
  },

  // ── 컬렉션 ──────────────────────────────────────────
  {
    method: "GET",
    path: "/api/collections",
    handler: () => db.collections.filter((c) => c.ownerId === ME_ID).map(collectionDto),
  },
  {
    method: "POST",
    path: "/api/collections",
    handler: ({ body }) => {
      const title = String(body?.title ?? "").trim();
      if (!title) throw new HttpError(400, "NO_TITLE", "컬렉션 이름을 입력해 주세요.");
      const row = {
        id: nextId("c"),
        ownerId: ME_ID,
        title,
        description: String(body.description ?? ""),
        coverImageUrl: null,
        visibility: body.visibility ?? "PRIVATE",
        pinColor: body.pinColor ?? "LIME",
        createdAt: new Date().toISOString(),
      };
      db.collections.push(row);
      return collectionDto(row);
    },
  },
  {
    method: "GET",
    path: "/api/collections/:id",
    handler: ({ params }) => {
      const c = collectionOr404(params.id);
      const mine = new Set(mySaved().map((s) => s.placeId));
      return {
        ...collectionDto(c),
        places: collectionPlaceIds(c).map((id) => ({ ...placeDto(findPlace(id)!), savedByMe: mine.has(id) })),
      };
    },
  },
  {
    method: "PATCH",
    path: "/api/collections/:id",
    handler: ({ params, body }) => {
      const c = myCollectionOr404(params.id);
      if (typeof body?.title === "string" && body.title.trim()) c.title = body.title.trim();
      if (typeof body?.description === "string") c.description = body.description;
      if (body?.visibility) c.visibility = body.visibility;
      if (body?.pinColor) c.pinColor = body.pinColor;
      // 컬렉션에서 빼기: 저장한 장소 자체는 지우지 않습니다.
      const remove: string[] = body?.removePlaceIds ?? [];
      for (const s of mySaved()) {
        if (remove.includes(s.placeId)) s.collectionIds = s.collectionIds.filter((id) => id !== c.id);
      }
      return collectionDto(c);
    },
  },
  {
    /** 컬렉션 삭제는 저장한 장소를 지우지 않고 연결만 끊습니다. */
    method: "DELETE",
    path: "/api/collections/:id",
    handler: ({ params }) => {
      const c = myCollectionOr404(params.id);
      db.collections = db.collections.filter((col) => col.id !== c.id);
      for (const s of mySaved()) s.collectionIds = s.collectionIds.filter((id) => id !== c.id);
      return undefined;
    },
  },
];
