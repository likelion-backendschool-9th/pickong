import type { Report, ReportAction, ReportTarget, Sanction, SanctionType } from "../_api/adminApi";

export const REPORT_TARGET: Record<ReportTarget, string> = { POST: "게시물", COMMENT: "댓글", USER: "사용자", COLLECTION: "컬렉션" };

const REPORT_REASON: Record<string, string> = {
  SPAM: "스팸 · 홍보",
  ABUSE: "욕설 · 비하",
  IMPERSONATION: "사칭",
  COPYRIGHT: "저작권 침해",
  INAPPROPRIATE: "부적절한 사진",
  ETC: "기타",
};
export const reportReasonLabel = (reason: string) => REPORT_REASON[reason] ?? reason;

const REPORT_VISIBILITY: Record<Exclude<Report["target"]["visibility"], "LIVE">, string> = {
  HIDDEN: "숨김",
  DELETED: "삭제됨",
  UNAVAILABLE: "확인할 수 없음",
};

/** 정상 콘텐츠는 배지를 붙이지 않습니다. */
export const reportVisibilityLabel = (visibility: Report["target"]["visibility"]) =>
  visibility === "LIVE" ? null : REPORT_VISIBILITY[visibility];

export const REPORT_ACTION: Record<ReportAction, string> = { DISMISS: "기각", HIDE_CONTENT: "콘텐츠 숨김", SANCTION_USER: "작성자 제재" };

export const SANCTION_TYPES: { value: SanctionType; label: string; desc: string }[] = [
  { value: "WARNING", label: "경고", desc: "알림만 보내고 이용은 그대로" },
  { value: "SUSPEND_7", label: "7일 정지", desc: "7일 동안 글 · 댓글 · 메시지를 쓸 수 없어요" },
  { value: "SUSPEND_30", label: "30일 정지", desc: "30일 동안 글 · 댓글 · 메시지를 쓸 수 없어요" },
  { value: "BAN", label: "영구 정지", desc: "로그인할 수 없어요" },
];

const pad = (n: number) => String(n).padStart(2, "0");

/** 제재 요약: "7일 정지 · 11.27까지", "경고", "영구 정지" */
export function sanctionSummary(s: Sanction): string {
  const label = SANCTION_TYPES.find((t) => t.value === s.type)?.label ?? s.type;
  if (!s.until) return label;
  const d = new Date(s.until);
  return `${label} · ${pad(d.getMonth() + 1)}.${pad(d.getDate())}까지`;
}
