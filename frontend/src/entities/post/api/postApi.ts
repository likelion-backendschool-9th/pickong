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
  feed: async (tab: FeedTab, cursor?: string, region?: string) => {
    // cursor, region은 undefined면 쿼리에 포함하지 않음
    const query: Record<string, string> = { tab };
    if (cursor) query.cursor = cursor;
    if (region) query.region = region;
    const page = await http.get<FeedPageDto>("/feeds", { query });
    return { items: page.items.map(toPost), nextCursor: page.nextCursor };
  },
  feedPlaces: async (tab: FeedTab, region?: string) =>
    (await http.get<FeedPlaceDto[]>("/feed/places", { query: region ? { tab, region } : { tab } })).map(toFeedPlace),
  get: async (id: string) => toPost(await http.get<PostDto>(`/posts/${id}`)),
  byUser: async (username: string) => (await http.get<PostDto[]>(`/users/${username}/posts`)).map(toPost),
  /** 방문 지도: 이 사용자의 게시물에 연결된 장소 */
  placesByUser: async (username: string) =>
    (await http.get<FeedPlaceDto[]>(`/users/${username}/places`)).map(toFeedPlace),
  create: async (input: CreatePostInput) => toPost(await http.post<PostDto>("/posts", input)),
  like: async (id: string) => toPost(await http.post<PostDto>(`/posts/${id}/like`)),
  unlike: async (id: string) => toPost(await http.delete<PostDto>(`/posts/${id}/like`)),
  bookmark: async (id: string) => toPost(await http.post<PostDto>(`/posts/${id}/bookmark`)),
  unbookmark: async (id: string) => toPost(await http.delete<PostDto>(`/posts/${id}/bookmark`)),
  comments: async (id: string) => (await http.get<CommentDto[]>(`/posts/${id}/comments`)).map(toComment),
  addComment: async (id: string, input: { content: string; parentId: string | null }) =>
    toComment(await http.post<CommentDto>(`/posts/${id}/comments`, input)),
  updateComment: async (postId: string, commentId: string, content: string) =>
    toComment(await http.put<CommentDto>(`/posts/${postId}/comments/${commentId}`, { content })),
  deleteComment: async (postId: string, commentId: string) =>
    http.delete<void>(`/posts/${postId}/comments/${commentId}`),
};
