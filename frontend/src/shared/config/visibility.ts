/** 공개 범위 — 게시물·컬렉션·개인 장소에 공통으로 적용하는 플랫폼 정책 */
export type Visibility = "PUBLIC" | "FOLLOWERS" | "PRIVATE";

export const VISIBILITY_OPTIONS: { value: Visibility; label: string; description: string }[] = [
  { value: "PUBLIC", label: "전체 공개", description: "모든 사용자 · 피드 지도에 공개될 수 있어요" },
  { value: "FOLLOWERS", label: "팔로워 공개", description: "나를 팔로우한 사람만 볼 수 있어요" },
  { value: "PRIVATE", label: "비공개", description: "나만 볼 수 있어요" },
];

export const visibilityLabel = (v: Visibility) =>
  VISIBILITY_OPTIONS.find((o) => o.value === v)?.label ?? v;
