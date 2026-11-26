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
  usernameChangeableAt,
} from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";
import { isUsernameTaken, USERNAME_PATTERN } from "./auth.ts";

const userOr404 = (username: string) => findUserByName(username) ?? notFound("사용자");

const ALL_CATEGORIES = ["CAFE", "RESTAURANT", "PHOTO_SPOT", "SHOP", "EXHIBITION", "STREET", "PARK", "ATTRACTION", "STATION", "ETC"];
const DEFAULT_MAP_CATEGORIES = ["CAFE", "RESTAURANT", "SHOP", "PARK", "PHOTO_SPOT", "EXHIBITION", "STREET"];

export const userRoutes: Route[] = [
  { method: "GET", path: "/api/v1/me", handler: () => userDto(db.users.find((u) => u.id === ME_ID)!) },
  {
    /**
     * 프로필 수정. 백엔드(PATCH /me)와 같은 규칙입니다.
     * - 보낸 항목만 바꿉니다. null · 빈 값이면 지웁니다(아이디는 지울 수 없음).
     * - 아이디: 형식 → 지금과 같으면 그대로(14일 제한도 시작하지 않음) → 사용 중 · 예약어 → 14일 제한 순서로 확인
     * - 항목을 모두 확인한 뒤 한 번에 반영합니다. 하나라도 틀리면 아무것도 바뀌지 않습니다.
     */
    method: "PATCH",
    path: "/api/v1/me",
    handler: ({ body }) => {
      const me = db.users.find((u) => u.id === ME_ID)!;
      const has = (key: string) => body !== null && typeof body === "object" && key in body;
      const text = (value: unknown, max: number, code: string, message: string) => {
        if (value !== null && typeof value !== "string") throw new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요.");
        const trimmed = (value ?? "").trim();
        if ([...trimmed].length > max) throw new HttpError(400, code, message);
        return trimmed;
      };

      let username = me.username;
      let usernameChanged = false;
      if (has("username")) {
        const value = typeof body.username === "string" ? body.username.trim().toLowerCase() : null;
        if (value === null || !USERNAME_PATTERN.test(value)) {
          throw new HttpError(400, "INVALID_USERNAME", "아이디는 영문으로 시작하는 3~20자로, 영문 소문자 · 숫자 · 밑줄(_) · 마침표(.)만 쓸 수 있어요.");
        }
        if (value !== me.username.toLowerCase()) {
          if (isUsernameTaken(value, me.id)) throw new HttpError(409, "USERNAME_TAKEN", "이미 사용 중인 아이디예요.");
          if (usernameChangeableAt(me)) throw new HttpError(409, "USERNAME_CHANGE_TOO_SOON", "아이디는 14일에 한 번만 바꿀 수 있어요.");
          username = value;
          usernameChanged = true;
        }
      }
      const name = has("name") ? text(body.name, 20, "INVALID_NAME", "이름은 20자까지 쓸 수 있어요.") || null : undefined;
      const bio = has("bio") ? text(body.bio, 150, "INVALID_BIO", "소개는 150자까지 쓸 수 있어요.") : undefined;
      const region = has("region") ? text(body.region, 20, "INVALID_REGION", "활동 지역은 20자까지 쓸 수 있어요.") : undefined;

      // 모두 통과했을 때만 반영
      if (usernameChanged) {
        me.username = username;
        me.usernameChangedAt = new Date().toISOString();
      }
      if (name !== undefined) me.name = name;
      if (bio !== undefined) me.bio = bio;
      if (region !== undefined) me.region = region;
      // 표시 이름(관리자 화면용) = 이름 ?? 아이디(username). fixture처럼 name이 undefined면 기존 nickname을 이름으로 봅니다.
      me.nickname = (me.name === undefined ? me.nickname : me.name) ?? me.username;
      return userDto(me);
    },
  },
  {
    /** 내 지도 카테고리 칩: 보이는 카테고리를 순서대로 */
    method: "GET",
    path: "/api/v1/me/map-categories",
    handler: () => ({ categories: db.mapCategories[ME_ID] ?? DEFAULT_MAP_CATEGORIES }),
  },
  {
    method: "PUT",
    path: "/api/v1/me/map-categories",
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
    path: "/api/v1/users/:username",
    handler: ({ params }) => userDto(userOr404(params.username)),
  },
  {
    method: "GET",
    path: "/api/v1/users/:username/feeds",
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
    path: "/api/v1/users/:username/collections",
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
    path: "/api/v1/users/:username/places",
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
    path: "/api/v1/users/:username/followers",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.follows
        .filter(([, b]) => b === user.id)
        .map(([a]) => userDto(db.users.find((u) => u.id === a)!));
    },
  },
  {
    method: "GET",
    path: "/api/v1/users/:username/following",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.follows
        .filter(([a]) => a === user.id)
        .map(([, b]) => userDto(db.users.find((u) => u.id === b)!));
    },
  },
  {
    method: "POST",
    path: "/api/v1/users/:username/follow",
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
    path: "/api/v1/users/:username/follow",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      if (isFollowing(ME_ID, user.id)) {
        db.follows = db.follows.filter(([a, b]) => !(a === ME_ID && b === user.id));
      }
      return userDto(user);
    },
  },
];
