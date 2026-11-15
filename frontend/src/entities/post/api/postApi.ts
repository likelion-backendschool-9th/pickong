import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";
import type { CommentDto, FeedPlaceDto, PostDto } from "./dto";
import { toComment, toFeedPlace, toPost } from "./mapper";

export type FeedTab = "following" | "recommended";

export interface CreatePostInput {
  photos: { url: string; placeId: string | null }[];
  content: string;
  visibility: Visibility;
}

export const postApi = {
  feed: async (tab: FeedTab) => (await http.get<PostDto[]>("/feed", { query: { tab } })).map(toPost),
  feedPlaces: async () => (await http.get<FeedPlaceDto[]>("/feed/places")).map(toFeedPlace),
  get: async (id: string) => toPost(await http.get<PostDto>(`/posts/${id}`)),
  byUser: async (username: string) => (await http.get<PostDto[]>(`/users/${username}/posts`)).map(toPost),
  create: async (input: CreatePostInput) => toPost(await http.post<PostDto>("/posts", input)),
  like: async (id: string) => toPost(await http.post<PostDto>(`/posts/${id}/like`)),
  unlike: async (id: string) => toPost(await http.delete<PostDto>(`/posts/${id}/like`)),
  bookmark: async (id: string) => toPost(await http.post<PostDto>(`/posts/${id}/bookmark`)),
  unbookmark: async (id: string) => toPost(await http.delete<PostDto>(`/posts/${id}/bookmark`)),
  comments: async (id: string) => (await http.get<CommentDto[]>(`/posts/${id}/comments`)).map(toComment),
  addComment: async (id: string, input: { content: string; parentId: string | null }) =>
    toComment(await http.post<CommentDto>(`/posts/${id}/comments`, input)),
};
