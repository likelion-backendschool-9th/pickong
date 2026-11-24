import { routes } from "@/shared/config/routes";
import type { PendingTerms, TermsType } from "../_api/consentApi";

/** 약관 종류 → 전문 화면 경로의 종류 */
export const TERMS_PATH: Record<TermsType, "service" | "privacy" | "marketing"> = {
  SERVICE: "service",
  PRIVACY: "privacy",
  MARKETING: "marketing",
};

/** 남은 약관 중 필수가 있는지 (있으면 동의할 때까지 서비스를 쓸 수 없음) */
export function hasRequired(pending: PendingTerms[]): boolean {
  return pending.some((terms) => terms.required);
}

/** 필수 약관에 모두 체크했는지. 선택 약관만 남았으면 체크하지 않아도 넘어갈 수 있습니다. */
export function canSubmit(pending: PendingTerms[], checked: ReadonlySet<TermsType>): boolean {
  return pending.every((terms) => !terms.required || checked.has(terms.type));
}

/** '모두 동의해요'를 누른 결과: 모두 체크돼 있으면 모두 해제, 아니면 모두 체크 */
export function toggleAll(pending: PendingTerms[], checked: ReadonlySet<TermsType>): Set<TermsType> {
  const allChecked = pending.every((terms) => checked.has(terms.type));
  return allChecked ? new Set() : new Set(pending.map((terms) => terms.type));
}

/**
 * 동의한 뒤 돌아갈 곳. 찜콩 안 경로(/로 시작)만 따르고, 다른 사이트 주소(//example.com, /\example.com)나
 * 재동의 화면 자신은 무시하고 피드로 보냅니다. (?next=를 조작해 다른 사이트로 보내는 것을 막기 위해)
 */
export function safeNext(next: string | undefined | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return routes.feed();
  if (next === routes.termsConsent() || next.startsWith(`${routes.termsConsent()}?`)) return routes.feed();
  return next;
}
