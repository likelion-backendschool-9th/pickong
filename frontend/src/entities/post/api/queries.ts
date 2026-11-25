import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import type { Post } from "../model";
import { postApi, type FeedTab } from "./postApi";

export const postKeys = {
  all: ["posts"] as const,
  feed: (tab: FeedTab, region?: string) => [...postKeys.all, "feed", tab, region ?? ""] as const,
  feedPlaces: (tab: FeedTab, region?: string) => [...postKeys.all, "feed-places", tab, region ?? ""] as const,
  detail: (id: string) => [...postKeys.all, "detail", id] as const,
  byUser: (username: string) => [...postKeys.all, "user", username] as const,
  placesByUser: (username: string) => [...postKeys.all, "user-places", username] as const,
  comments: (id: string) => [...postKeys.all, "comments", id] as const,
};

export const useFeed = (tab: FeedTab, region?: string) =>
  useQuery({ queryKey: postKeys.feed(tab, region), queryFn: () => postApi.feed(tab, region) });
/** 피드 지도의 장소: 피드 탭과 같은 기준의 게시물에 연결된 장소 */
export const useFeedPlaces = (tab: FeedTab, region?: string) =>
  useQuery({ queryKey: postKeys.feedPlaces(tab, region), queryFn: () => postApi.feedPlaces(tab, region) });
export const usePost = (id: string) => useQuery({ queryKey: postKeys.detail(id), queryFn: () => postApi.get(id) });
export const useUserPosts = (username: string) =>
  useQuery({ queryKey: postKeys.byUser(username), queryFn: () => postApi.byUser(username) });
/** 방문 지도: 이 사용자의 게시물에 연결된 장소 (게시물을 올리면 postKeys.all 무효화로 함께 갱신) */
export const useUserPlaces = (username: string) =>
  useQuery({ queryKey: postKeys.placesByUser(username), queryFn: () => postApi.placesByUser(username) });
export const useComments = (id: string, enabled = true) =>
  useQuery({ queryKey: postKeys.comments(id), queryFn: () => postApi.comments(id), enabled });

/**
 * 캐시에 있는 같은 게시물을 모두 갱신합니다.
 * 목록을 다시 불러오지 않으므로 피드 스크롤 위치와 보고 있던 사진 번호가 그대로 유지됩니다.
 */
export function patchPostInCache(qc: QueryClient, id: string, patch: (post: Post) => Post) {
  qc.setQueriesData<Post[] | Post>({ queryKey: postKeys.all }, (data) => {
    if (!data) return data;
    if (Array.isArray(data)) return data.map((p) => (p?.id === id ? patch(p) : p));
    return (data as Post).id === id ? patch(data as Post) : data;
  });
}

export function useCreatePost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: postApi.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: postKeys.all }),
  });
}

export function useAddComment(postId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { content: string; parentId: string | null }) => postApi.addComment(postId, input),
    onSuccess: (comment) => {
      qc.setQueryData(postKeys.comments(postId), (prev: unknown[] | undefined) => [...(prev ?? []), comment]);
      patchPostInCache(qc, postId, (p) => ({ ...p, commentCount: p.commentCount + 1 }));
    },
  });
}
