import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import type { Post } from "../model";
import { postApi, type FeedTab } from "./postApi";

export const postKeys = {
  all: ["posts"] as const,
  feed: (tab: FeedTab) => [...postKeys.all, "feed", tab] as const,
  feedPlaces: () => [...postKeys.all, "feed-places"] as const,
  detail: (id: string) => [...postKeys.all, "detail", id] as const,
  byUser: (username: string) => [...postKeys.all, "user", username] as const,
  comments: (id: string) => [...postKeys.all, "comments", id] as const,
};

export const useFeed = (tab: FeedTab) => useQuery({ queryKey: postKeys.feed(tab), queryFn: () => postApi.feed(tab) });
export const useFeedPlaces = () => useQuery({ queryKey: postKeys.feedPlaces(), queryFn: postApi.feedPlaces });
export const usePost = (id: string) => useQuery({ queryKey: postKeys.detail(id), queryFn: () => postApi.get(id) });
export const useUserPosts = (username: string) =>
  useQuery({ queryKey: postKeys.byUser(username), queryFn: () => postApi.byUser(username) });
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
