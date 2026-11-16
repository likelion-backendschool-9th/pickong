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
import { notFound, type Route } from "../router.ts";

const userOr404 = (username: string) => findUserByName(username) ?? notFound("사용자");

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
    /** 방문 기록: 본인만 볼 수 있으므로 내 계정 경로로만 제공합니다. */
    method: "GET",
    path: "/api/me/visits",
    handler: () => {
      return db.plans
        .filter((p) => p.ownerId === ME_ID && p.status === "DONE")
        .map((p) => {
          const visited = p.items.filter((i) => i.visited).map((i) => placeDto(findPlace(i.placeId)!));
          return {
            planId: p.id,
            title: p.title,
            date: p.date,
            region: p.region,
            visitedCount: visited.length,
            places: visited,
            coverImageUrl: visited.find((pl) => pl.imageUrl)?.imageUrl ?? null,
          };
        });
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
