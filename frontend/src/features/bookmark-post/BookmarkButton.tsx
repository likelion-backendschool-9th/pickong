"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Bookmark } from "lucide-react";
import { patchPostInCache, postApi, type Post } from "@/entities/post";
import { cn } from "@/shared/lib/cn";

/**
 * 글 저장(북마크): 게시물 자체를 저장합니다.
 * 장소 찜콩과 독립적이며, 이 버튼으로는 장소가 저장되지 않습니다.
 */
export function BookmarkButton({ post }: { post: Post }) {
  const qc = useQueryClient();
  const toggle = useMutation({
    mutationFn: (saved: boolean) => (saved ? postApi.unbookmark(post.id) : postApi.bookmark(post.id)),
    onMutate: (saved) => patchPostInCache(qc, post.id, (p) => ({ ...p, isBookmarked: !saved })),
    onError: (_e, saved) => patchPostInCache(qc, post.id, (p) => ({ ...p, isBookmarked: saved })),
  });

  return (
    <button
      type="button"
      aria-label={post.isBookmarked ? "글 저장 취소" : "글 저장"}
      aria-pressed={post.isBookmarked}
      onClick={() => toggle.mutate(post.isBookmarked)}
      className="inline-flex h-11 min-w-11 flex-col items-center justify-center rounded-full px-1 active:bg-fill"
    >
      <Bookmark
        size={22}
        strokeWidth={1.6}
        className={cn(post.isBookmarked ? "fill-primary-deep text-primary-deep" : "text-ink")}
      />
      <span className="text-[10px] text-ink-sub">글 저장</span>
    </button>
  );
}
