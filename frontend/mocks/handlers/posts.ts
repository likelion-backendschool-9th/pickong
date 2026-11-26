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
 * - region: 게시물에 연결된 장소 중 하나라도 해당 지역이면 포함 (없거나 "전국"이면 전체)
 */
const feedPosts = (tab: string, region?: string | null) => {
  const posts = db.posts
    .filter((p) => canView(p.authorId, p.visibility))
    .filter((p) =>
      tab === "recommended"
        ? p.authorId !== ME_ID && p.visibility === "PUBLIC"
        : p.authorId === ME_ID || isFollowing(ME_ID, p.authorId),
    )
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  if (!region) return posts;

  // 쉼표 구분 다중 지역. 게시물 장소가 선택된 지역 중 하나라도 startsWith이면 포함
  const regions = region.split(",").filter(Boolean);
  if (regions.length === 0) return posts;

  return posts.filter((p) =>
    p.photos.some((ph) => {
      if (!ph.placeId) return false;
      const place = findPlace(ph.placeId);
      return place && regions.some((r) => place.region?.startsWith(r));
    }),
  );
};

export const postRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/feed",
    delay: 300,
    handler: ({ query }) => feedPosts(query.get("tab") ?? "following", query.get("region")).map(postDto),
  },
  {
    /** 피드 지도: 피드 탭(팔로잉 · 추천)과 같은 기준의 게시물에 연결된 장소 (내 지도와 별개). postIds는 최신순 */
    method: "GET",
    path: "/api/v1/feed/places",
    delay: 300,
    handler: ({ query }) => {
      const byPlace = new Map<string, string[]>();
      for (const post of feedPosts(query.get("tab") ?? "following", query.get("region"))) {
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
        .map((c) => ({ ...c, deleted: c.deleted ?? false, author: userDto(findUser(c.authorId)!) }));
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
        deleted: false,
        createdAt: new Date().toISOString(),
      };
      db.comments.push(comment);
      return { ...comment, author: userDto(findUser(ME_ID)!) };
    },
  },
  {
    method: "PUT",
    path: "/api/v1/posts/:id/comments/:commentId",
    handler: ({ params, body }) => {
      postOr404(params.id);
      const idx = db.comments.findIndex((c) => c.id === params.commentId && c.postId === params.id);
      if (idx === -1) throw new HttpError(404, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
      if (db.comments[idx].authorId !== ME_ID) throw new HttpError(403, "COMMENT_NOT_OWNER", "본인 댓글만 수정할 수 있습니다.");
      const content = String(body?.content ?? "").trim();
      if (!content) throw new HttpError(400, "EMPTY", "댓글 내용을 입력해 주세요.");
      db.comments[idx] = { ...db.comments[idx], content };
      return { ...db.comments[idx], author: userDto(findUser(ME_ID)!) };
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/posts/:id/comments/:commentId",
    handler: ({ params }) => {
      postOr404(params.id);
      const idx = db.comments.findIndex((c) => c.id === params.commentId && c.postId === params.id);
      if (idx === -1) throw new HttpError(404, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
      if (db.comments[idx].authorId !== ME_ID) throw new HttpError(403, "COMMENT_NOT_OWNER", "본인 댓글만 삭제할 수 있습니다.");
      const commentId = params.commentId;
      const hasReplies = db.comments.some((c) => c.parentId === commentId && !c.deleted);
      if (hasReplies) {
        // 살아있는 대댓글이 있으면 소프트 삭제 (목록에 "삭제된 댓글입니다"로 남음)
        db.comments[idx] = { ...db.comments[idx], deleted: true, content: null };
      } else {
        // 대댓글이 없으면 목록에서 제거
        db.comments = db.comments.filter((c) => c.id !== commentId);
      }
      return null;
    },
  },
];
