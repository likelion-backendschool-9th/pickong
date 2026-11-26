import { compareFirstLine, compareFull, excerpt } from "./reportContentComparison.ts";

export type ReportTargetType = "POST" | "COMMENT" | "USER" | "COLLECTION";
export type ReportVisibility = "LIVE" | "HIDDEN" | "DELETED" | "UNAVAILABLE";

/** Mock 안에만 두는 비교 범위. 관리자 API 응답에는 넣지 않습니다. */
export interface ReportSnapshotSource {
  text: string | null;
  imageUrl: string | null;
  textScope?: string | null;
}

export interface ReportTargetResponse {
  text: string;
  imageUrl: string | null;
  link: string | null;
  snapshot: { text: string | null; imageUrl: string | null } | null;
  current: { text: string | null; imageUrl: string | null } | null;
  modified: boolean | null;
  visibility: ReportVisibility;
}

/** 대상 행이 없어도 currentBody를 준 Mock 사례는 공개 상태로 비교합니다. */
export function resolveMockVisibility(
  explicit: ReportVisibility | undefined,
  source: ReportVisibility,
  hasCurrentBody: boolean,
): ReportVisibility {
  if (explicit) return explicit;
  if (hasCurrentBody && source === "UNAVAILABLE") return "LIVE";
  return source;
}

export function buildReportTarget(input: {
  targetType: ReportTargetType;
  visibility: ReportVisibility;
  snapshot: ReportSnapshotSource | null;
  currentBody: string | null;
  link: string | null;
  liveFallback: string;
  deletedText: string;
  unavailableText: string;
}): ReportTargetResponse {
  const readable = input.visibility === "LIVE" || input.visibility === "HIDDEN";
  const evidence = excerpt(input.snapshot?.text ?? null);
  const text =
    evidence ??
    (input.visibility === "DELETED"
      ? input.deletedText
      : input.visibility === "UNAVAILABLE"
        ? input.unavailableText
        : input.liveFallback);
  return {
    text,
    imageUrl: input.snapshot?.imageUrl ?? null,
    link: input.visibility === "LIVE" ? input.link : null,
    snapshot: input.snapshot ? { text: input.snapshot.text, imageUrl: input.snapshot.imageUrl } : null,
    current: readable ? { text: input.currentBody, imageUrl: null } : null,
    modified: modifiedOf(input, readable),
    visibility: input.visibility,
  };
}

function modifiedOf(
  input: {
    targetType: ReportTargetType;
    visibility: ReportVisibility;
    snapshot: ReportSnapshotSource | null;
    currentBody: string | null;
  },
  readable: boolean,
): boolean | null {
  if (!readable || input.snapshot == null) return null;
  if (input.targetType === "USER") return compareFirstLine(input.snapshot.text, input.currentBody);
  if (input.targetType !== "POST" && input.targetType !== "COMMENT") return null;
  if (input.snapshot.textScope === "FULL") return compareFull(input.snapshot.text, input.currentBody);
  return compareFirstLine(input.snapshot.text, input.currentBody);
}

/** 두 줄을 넘거나 한 줄이 길면 카드 안에서 펼칩니다. */
export function reportBodyNeedsToggle(text: string): boolean {
  return text.split("\n").length > 2 || text.length > 80;
}

export function reportChangeLabel(targetType: ReportTargetType): string {
  return targetType === "POST" || targetType === "COMMENT" ? "본문 변경 감지" : "내용 변경 감지";
}
