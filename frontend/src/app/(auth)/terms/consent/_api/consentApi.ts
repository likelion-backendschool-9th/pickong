import { useMutation, useQuery } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

export type TermsType = "SERVICE" | "PRIVACY" | "MARKETING";

/** 지금 동의를 받아야 하는 약관 하나 (지금 시행 중인 버전). 필수면 동의할 때까지 서비스를 쓸 수 없습니다. */
export interface PendingTerms {
  type: TermsType;
  title: string;
  required: boolean;
  version: string;
}

/** 동의 받을 약관. 없으면 빈 배열(재동의할 것이 없음) */
export const usePendingTerms = () =>
  useQuery({ queryKey: ["me", "agreements", "pending"], queryFn: () => http.get<PendingTerms[]>("/me/agreements/pending") });

/**
 * 재동의 화면의 동의를 한 번에 보냅니다. 동의 받을 필수 약관이 빠지면 400 REQUIRED_TERMS_NOT_AGREED.
 * 다시 물은 선택 약관을 보내지 않으면 서버가 예전 동의를 철회합니다(거절).
 */
export const useAgreePendingTerms = () =>
  useMutation({ mutationFn: (agreedTerms: TermsType[]) => http.post<unknown>("/me/agreements", { agreedTerms }) });
