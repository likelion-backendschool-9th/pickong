import { canView, db, findPlace, findUser, isFollowing, ME_ID, nextId, placeDto, postDto, userDto } from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";

const postOr404 = (id: string) => {
  const post = db.posts.find((p) => p.id === id);
  if (!post || !canView(post.authorId, post.visibility)) return notFound("게시물");
  return post;
};

/**
 * 피드에 보이는 게시물 (최신순). 목록과 지도가 같은 기준을 씁니다.
 * - following: 나와 내가 팔로우하는 사람의 게시물 (볼 수 있는 것만)
 * - recommended: 다른 사람의 전체 공개 게시물
 */
const feedPosts = (tab: string) =>
  db.posts
    .filter((p) => canView(p.authorId, p.visibility))
    .filter((p) =>
      tab === "recommended"
        ? p.authorId !== ME_ID && p.visibility === "PUBLIC"
        : p.authorId === ME_ID || isFollowing(ME_ID, p.authorId),
    )
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

export const postRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/feed",
    delay: 300,
    handler: ({ query }) => feedPosts(query.get("tab") ?? "following").map(postDto),
  },
  {
    /** 피드 지도: 피드 탭(팔로잉 · 추천)과 같은 기준의 게시물에 연결된 장소 (내 지도와 별개). postIds는 최신순 */
    method: "GET",
    path: "/api/v1/feed/places",
    delay: 300,
    handler: ({ query }) => {
      const byPlace = new Map<string, string[]>();
      for (const post of feedPosts(query.get("tab") ?? "following")) {
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
  { method: "GET", path: "/api/v1/posts/:id", handler: ({ params }) => postDto(postOr404(params.id)) },
  {
    method: "POST",
    path: "/api/v1/posts",
    delay: 600,
    handler: ({ body }) => {
      const photos = Array.isArray(body?.photos) ? body.photos : [];
      if (photos.length === 0) throw new HttpError(400, "NO_PHOTO", "사진을 1장 이상 선택해 주세요.");
      for (const ph of photos) {
        if (ph.placeId && !findPlace(ph.placeId)) throw new HttpError(400, "BAD_PLACE", "연결한 장소를 찾을 수 없어요.");
      }
      const post = {
        id: nextId("post"),
        authorId: ME_ID,
        photos: photos.map((ph: { url: string; placeId: string | null }) => ({ url: ph.url, placeId: ph.placeId ?? null })),
        content: String(body.content ?? ""),
        visibility: body.visibility ?? "PUBLIC",
        likeCount: 0,
        likedByMe: false,
        bookmarkedByMe: false,
        createdAt: new Date().toISOString(),
      };
      db.posts.unshift(post);
      return postDto(post);
    },
  },
  {
    method: "POST",
    path: "/api/v1/posts/:id/like",
    handler: ({ params }) => {
      const post = postOr404(params.id);
      if (!post.likedByMe) {
        post.likedByMe = true;
        post.likeCount += 1;
      }
      return postDto(post);
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/posts/:id/like",
    handler: ({ params }) => {
      const post = postOr404(params.id);
      if (post.likedByMe) {
        post.likedByMe = false;
        post.likeCount -= 1;
      }
      return postDto(post);
    },
  },
  {
    method: "POST",
    path: "/api/v1/posts/:id/bookmark",
    handler: ({ params }) => {
      const post = postOr404(params.id);
      post.bookmarkedByMe = true;
      return postDto(post);
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/posts/:id/bookmark",
    handler: ({ params }) => {
      const post = postOr404(params.id);
      post.bookmarkedByMe = false;
      return postDto(post);
    },
  },
  {
    method: "GET",
    path: "/api/v1/posts/:id/comments",
    handler: ({ params }) => {
      postOr404(params.id);
      return db.comments
        .filter((c) => c.postId === params.id)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
        .map((c) => ({ ...c, author: userDto(findUser(c.authorId)!) }));
    },
  },
  {
    method: "POST",
    path: "/api/v1/posts/:id/comments",
    handler: ({ params, body }) => {
      postOr404(params.id);
      const content = String(body?.content ?? "").trim();
      if (!content) throw new HttpError(400, "EMPTY", "댓글 내용을 입력해 주세요.");
      const comment = {
        id: nextId("cm"),
        postId: params.id,
        authorId: ME_ID,
        parentId: body?.parentId ?? null,
        content,
        createdAt: new Date().toISOString(),
      };
      db.comments.push(comment);
      return { ...comment, author: userDto(findUser(ME_ID)!) };
    },
  },
];
