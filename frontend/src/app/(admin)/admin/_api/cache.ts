import type { QueryClient } from "@tanstack/react-query";
import { postKeys } from "@/entities/post";

export const adminKey = ["admin"] as const;

/** UUID와 기존 게시물 ID가 섞여 있으므로 특정 키를 추측하지 않고 게시물·댓글 캐시 전체를 무효화합니다. */
export function invalidateModerationQueries(client: QueryClient) {
  return Promise.all([
    client.invalidateQueries({ queryKey: adminKey }),
    client.invalidateQueries({ queryKey: postKeys.all }),
  ]);
}
