"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Heart } from "lucide-react";
import { patchPostInCache, postApi, type Post } from "@/entities/post";
import { cn } from "@/shared/lib/cn";

/** 좋아요. 화면에 먼저 반영하고 실패하면 되돌립니다. */
export function LikeButton({ post }: { post: Post }) {
  const qc = useQueryClient();
  const toggle = useMutation({
    mutationFn: (liked: boolean) => (liked ? postApi.unlike(post.id) : postApi.like(post.id)),
    onMutate: (liked) => {
      patchPostInCache(qc, post.id, (p) => ({ ...p, isLiked: !liked, likeCount: p.likeCount + (liked ? -1 : 1) }));
    },
    onError: (_e, liked) => {
      patchPostInCache(qc, post.id, (p) => ({ ...p, isLiked: liked, likeCount: p.likeCount + (liked ? 1 : -1) }));
    },
  });

  return (
    <button
      type="button"
      aria-label={post.isLiked ? "좋아요 취소" : "좋아요"}
      aria-pressed={post.isLiked}
      onClick={() => toggle.mutate(post.isLiked)}
      className="inline-flex size-11 items-center justify-center rounded-full active:bg-fill"
    >
      <Heart
        size={24}
        strokeWidth={1.6}
        className={cn(post.isLiked ? "fill-primary-deep text-primary-deep" : "text-ink")}
      />
    </button>
  );
}
