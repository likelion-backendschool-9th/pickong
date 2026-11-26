import { canView, collectionDto, db, findUser, placeDto, postDto, userDto } from "../db.ts";
import { searchPlaces } from "../lib/placeSearch.ts";
import type { Route } from "../router.ts";

const norm = (s: string) => s.replace(/\s+/g, "").toLowerCase();
const PLACE_SEARCH_LIMIT = 50;

export const discoverRoutes: Route[] = [
  {
    /** 통합 검색: 장소 / 사용자 / 게시물 / 컬렉션 */
    method: "GET",
    path: "/api/v1/search",
    delay: 250,
    handler: ({ query }) => {
      const q = norm(query.get("q") ?? "");
      const type = query.get("type") ?? "place";
      if (!q) return { type, items: [] };

      if (type === "user") {
        return {
          type,
          items: db.users
            .filter((u) => norm(u.nickname + u.username + u.region + u.bio).includes(q))
            .map(userDto),
        };
      }
      if (type === "post") {
        return {
          type,
          items: db.posts
            .filter((p) => canView(p.authorId, p.visibility))
            .filter((p) => {
              const placeNames = p.photos.map((ph) => db.places.find((pl) => pl.id === ph.placeId)?.name ?? "");
              return norm(p.content + placeNames.join("") + (findUser(p.authorId)?.region ?? "")).includes(q);
            })
            .map(postDto),
        };
      }
      if (type === "collection") {
        return {
          type,
          items: db.collections
            .filter((c) => canView(c.ownerId, c.visibility))
            .filter((c) => norm(c.title + c.description).includes(q))
            .map(collectionDto),
        };
      }
      // 실제 상가 정보가 들어 있어 "카페"처럼 넓은 검색어는 수천 곳이 걸립니다. 가까운 순으로 50곳까지만 돌려줍니다.
      const candidates = db.places.filter((p) => p.category !== "STATION" && canView(p.ownerId ?? "", p.visibility));
      return {
        type: "place",
        items: searchPlaces(candidates, q, PLACE_SEARCH_LIMIT).map(placeDto),
      };
    },
  },
  {
    method: "GET",
    path: "/api/v1/notifications",
    handler: () =>
      db.notifications.map((n) => {
        const actor = n.actorId ? findUser(n.actorId) : null;
        return { ...n, actor: actor ? userDto(actor) : null };
      }),
  },
  {
    method: "POST",
    path: "/api/v1/notifications/read",
    handler: () => {
      for (const n of db.notifications) n.read = true;
      return undefined;
    },
  },
];
