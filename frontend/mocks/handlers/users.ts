import {
  canView,
  collectionDto,
  db,
  findPlace,
  findUserByName,
  isFollowing,
  ME_ID,
  placeDto,
  postDto,
  userDto,
} from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";

const userOr404 = (username: string) => findUserByName(username) ?? notFound("사용자");

const ALL_CATEGORIES = ["CAFE", "RESTAURANT", "PHOTO_SPOT", "SHOP", "EXHIBITION", "STREET", "PARK", "ATTRACTION", "STATION", "ETC"];
const DEFAULT_MAP_CATEGORIES = ["CAFE", "RESTAURANT", "SHOP", "PARK", "PHOTO_SPOT", "EXHIBITION", "STREET"];

export const userRoutes: Route[] = [
  { method: "GET", path: "/api/me", handler: () => userDto(db.users.find((u) => u.id === ME_ID)!) },
  {
    method: "PATCH",
    path: "/api/me",
    handler: ({ body }) => {
      const me = db.users.find((u) => u.id === ME_ID)!;
      if (typeof body?.nickname === "string") me.nickname = body.nickname;
      if (typeof body?.bio === "string") me.bio = body.bio;
      if (typeof body?.region === "string") me.region = body.region;
      return userDto(me);
    },
  },
  {
    /** 내 지도 카테고리 칩: 보이는 카테고리를 순서대로 */
    method: "GET",
    path: "/api/me/map-categories",
    handler: () => ({ categories: db.mapCategories[ME_ID] ?? DEFAULT_MAP_CATEGORIES }),
  },
  {
    method: "PUT",
    path: "/api/me/map-categories",
    handler: ({ body }) => {
      const list: unknown = body?.categories;
      if (!Array.isArray(list) || list.some((c) => !ALL_CATEGORIES.includes(c)) || new Set(list).size !== list.length) {
        throw new HttpError(400, "BAD_CATEGORIES", "카테고리 설정을 확인해 주세요.");
      }
      db.mapCategories[ME_ID] = list;
      return { categories: list };
    },
  },
  {
    method: "GET",
    path: "/api/users/:username",
    handler: ({ params }) => userDto(userOr404(params.username)),
  },
  {
    method: "GET",
    path: "/api/users/:username/posts",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.posts
        .filter((p) => p.authorId === user.id && canView(p.authorId, p.visibility))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(postDto);
    },
  },
  {
    method: "GET",
    path: "/api/users/:username/collections",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.collections
        .filter((c) => c.ownerId === user.id && canView(c.ownerId, c.visibility))
        .map(collectionDto);
    },
  },
  {
    /**
     * 방문 지도: 이 사용자의 게시물에 연결된 장소. 내가 볼 수 있는 게시물만 포함합니다.
     * 저장 장소(내 지도)와는 별개이며, postIds는 최신 게시물부터입니다.
     */
    method: "GET",
    path: "/api/users/:username/places",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      const byPlace = new Map<string, string[]>();
      const posts = db.posts
        .filter((p) => p.authorId === user.id && canView(p.authorId, p.visibility))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      for (const post of posts) {
        for (const ph of post.photos) {
          if (!ph.placeId) continue;
          const list = byPlace.get(ph.placeId) ?? [];
          if (!list.includes(post.id)) list.push(post.id);
          byPlace.set(ph.placeId, list);
        }
      }
      return [...byPlace.entries()].map(([placeId, postIds]) => ({
        place: placeDto(findPlace(placeId)!),
        postIds,
        postCount: postIds.length,
      }));
    },
  },
  {
    method: "GET",
    path: "/api/users/:username/followers",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.follows
        .filter(([, b]) => b === user.id)
        .map(([a]) => userDto(db.users.find((u) => u.id === a)!));
    },
  },
  {
    method: "GET",
    path: "/api/users/:username/following",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.follows
        .filter(([a]) => a === user.id)
        .map(([, b]) => userDto(db.users.find((u) => u.id === b)!));
    },
  },
  {
    method: "POST",
    path: "/api/users/:username/follow",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      if (user.id !== ME_ID && !isFollowing(ME_ID, user.id)) {
        db.follows.push([ME_ID, user.id]);
      }
      return userDto(user);
    },
  },
  {
    method: "DELETE",
    path: "/api/users/:username/follow",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      if (isFollowing(ME_ID, user.id)) {
        db.follows = db.follows.filter(([a, b]) => !(a === ME_ID && b === user.id));
      }
      return userDto(user);
    },
  },
];
