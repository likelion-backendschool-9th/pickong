import type { PostComment } from "./model";

export interface RemoveCommentResult {
  // 삭제 후 남을 댓글 목록
  next: PostComment[];
  // commentCount에서 차감할 수 (백엔드는 대상 댓글 1개만 소프트 삭제)
  removedCount: number;
}

/**
 * 댓글 삭제 후 캐시에 반영할 목록과 카운터 감소량을 계산함.
 *
 * 백엔드 정책:
 * - 대상 댓글 하나만 소프트 삭제 (deleted_at 세팅)
 * - 살아있는 대댓글이 있으면 "삭제된 댓글입니다"로 목록에 남음
 * - 살아있는 대댓글이 없으면 목록에서 제거됨
 *
 * 프론트 캐시 갱신:
 * - 대댓글이 있으면 → deleted: true 로 마킹
 * - 대댓글이 없으면 → 목록에서 제거
 * - commentCount는 항상 1만 감소
 */
export function removeComment(comments: PostComment[], commentId: string): RemoveCommentResult {
  const hasReplies = comments.some((c) => c.parentId === commentId && !c.deleted);

  let next: PostComment[];
  if (hasReplies) {
    // 대댓글이 남아있으면 "삭제된 댓글입니다"로 마킹
    next = comments.map((c) =>
      c.id === commentId ? { ...c, deleted: true, content: null } : c,
    );
  } else {
    // 대댓글이 없으면 목록에서 제거
    next = comments.filter((c) => c.id !== commentId);
  }

  return { next, removedCount: 1 };
}
