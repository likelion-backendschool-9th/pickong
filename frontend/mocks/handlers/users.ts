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
    method: "GET",
    path: "/api/users/:username/visits",
    handler: ({ params }) => {
      const user = userOr404(params.username);
      return db.plans
        .filter((p) => p.ownerId === user.id && p.status === "DONE")
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
