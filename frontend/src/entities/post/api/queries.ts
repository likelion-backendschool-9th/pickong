import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData, type QueryClient } from "@tanstack/react-query";
import type { Post, PostComment } from "../model";
import { removeComment } from "../commentUtils";
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
  useInfiniteQuery({
    queryKey: postKeys.feed(tab, region),
    // pageParam: 이전 페이지의 nextCursor. 첫 요청은 undefined
    queryFn: ({ pageParam }) => postApi.feed(tab, pageParam, region),
    initialPageParam: undefined as string | undefined,
    // 마지막 페이지의 nextCursor가 null이면 undefined 반환 → 더 이상 fetchNextPage 안 함
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
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
 *
 * useFeed는 useInfiniteQuery라 캐시가 { pages: [{items:[...]}, ...] } 구조임.
 * 단건 조회(usePost)는 Post 단일 객체 구조.
 * 두 형태 모두 처리함.
 */
export function patchPostInCache(qc: QueryClient, id: string, patch: (post: Post) => Post) {
  // 1. 무한 스크롤 피드 캐시: { pages: [{ items: Post[], nextCursor }, ...], pageParams: [...] }
  qc.setQueriesData<InfiniteData<{ items: Post[]; nextCursor: string | null }>>(
    { queryKey: postKeys.all },
    (data) => {
      if (!data || !("pages" in data)) return data;
      return {
        ...data,
        pages: data.pages.map((page) => ({
          ...page,
          items: page.items.map((p) => (p.id === id ? patch(p) : p)),
        })),
      };
    },
  );

  // 2. Post[] 목록 캐시 (useUserPosts 등) — 프로필 탭에서도 좋아요·북마크 반영
  qc.setQueriesData<Post[]>({ queryKey: postKeys.all }, (data) => {
    if (!Array.isArray(data)) return data;
    return data.map((p) => (p.id === id ? patch(p) : p));
  });

  // 3. 단건 조회 캐시 (usePost)
  qc.setQueriesData<Post>({ queryKey: postKeys.detail(id) }, (data) => {
    if (!data) return data;
    return patch(data);
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

export function useUpdateComment(postId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ commentId, content }: { commentId: string; content: string }) =>
      postApi.updateComment(postId, commentId, content),
    onSuccess: (updated) => {
      qc.setQueryData(postKeys.comments(postId), (prev: PostComment[] | undefined) =>
        prev?.map((c) => (c.id === updated.id ? updated : c)) ?? [],
      );
    },
  });
}

export function useDeleteComment(postId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) => postApi.deleteComment(postId, commentId),
    onSuccess: (_, commentId) => {
      // 현재 캐시에서 결과 계산 후 두 캐시를 각각 갱신 (setQueryData updater는 순수하게 유지)
      const prev = qc.getQueryData<PostComment[]>(postKeys.comments(postId));
      const { next, removedCount } = removeComment(prev ?? [], commentId);
      qc.setQueryData(postKeys.comments(postId), next);
      patchPostInCache(qc, postId, (p) => ({ ...p, commentCount: Math.max(0, p.commentCount - removedCount) }));
    },
  });
}
