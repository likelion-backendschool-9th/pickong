"use client";

import { usePost } from "@/entities/post";
import { PostCard } from "@/widgets/post-card";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";

/** 게시물 상세: 피드 카드와 같은 동작(사진별 장소 · 찜콩 · 댓글 시트)을 유지합니다. */
export function PostDetailScreen({ id }: { id: string }) {
  const { data: post, isPending, isError, error } = usePost(id);
  return (
    <div>
      <TopBar title="게시물" backHref={routes.feed()} />
      {isPending ? (
        <Skeleton className="aspect-[13/10] w-full rounded-none" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} />
      ) : (
        <PostCard post={post} priority />
      )}
    </div>
  );
}
