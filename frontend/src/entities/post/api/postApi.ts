import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";
import type { CommentDto, FeedPageDto, FeedPlaceDto, PostDto } from "./dto";
import { toComment, toFeedPlace, toPost } from "./mapper";

export type FeedTab = "following" | "recommended";

export interface CreatePostInput {
  photos: { url: string; placeId: string | null }[];
  content: string;
  visibility: Visibility;
}

export const postApi = {
  feed: async (tab: FeedTab, cursor?: number, region?: string) => {
    // limit은 백엔드 필수 파라미터 (1~50), cursor는 이전 페이지의 nextCursor (숫자)
    const query: Record<string, string> = { tab, limit: "10" };
    if (cursor != null) query.cursor = String(cursor);
    if (region) query.region = region;
    const page = await http.get<FeedPageDto>("/feeds", { query });
    return { items: page.feeds.map(toPost), nextCursor: page.nextCursor };
  },
  feedPlaces: async (tab: FeedTab, region?: string) =>
    (await http.get<FeedPlaceDto[]>("/feeds/places", { query: region ? { tab, region } : { tab } })).map(toFeedPlace),
  get: async (id: string) => toPost(await http.get<PostDto>(`/feeds/${id}`)),
  byUser: async (username: string) => (await http.get<PostDto[]>(`/users/${username}/feeds`)).map(toPost),
  /** 방문 지도: 이 사용자의 게시물에 연결된 장소 */
  placesByUser: async (username: string) =>
    (await http.get<FeedPlaceDto[]>(`/users/${username}/places`)).map(toFeedPlace),
  create: async (input: CreatePostInput) => toPost(await http.post<PostDto>("/feeds", input)),
  // 좋아요·북마크: 백엔드가 201/204 반환 (응답 본문 없음). UI는 optimistic update로 처리
  like: async (id: string) => http.post<void>(`/feeds/${id}/likes`),
  unlike: async (id: string) => http.delete<void>(`/feeds/${id}/likes`),
  bookmark: async (id: string) => http.post<void>(`/feeds/${id}/bookmarks`),
  unbookmark: async (id: string) => http.delete<void>(`/feeds/${id}/bookmarks`),
  comments: async (id: string) => (await http.get<CommentDto[]>(`/feeds/${id}/comments`)).map(toComment),
  addComment: async (id: string, input: { content: string; parentId: string | null }) =>
    toComment(await http.post<CommentDto>(`/feeds/${id}/comments`, input)),
  updateComment: async (postId: string, commentId: string, content: string) =>
    toComment(await http.put<CommentDto>(`/feeds/${postId}/comments/${commentId}`, { content })),
  deleteComment: async (postId: string, commentId: string) =>
    http.delete<void>(`/feeds/${postId}/comments/${commentId}`),
};
