import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { userKeys } from "@/entities/user";
import { http } from "@/shared/api/http";

/* 크리에이터: 신청 · 심사 결과 · 정산 계좌 · 수익 */

export type CreatorStatus = "NONE" | "PENDING" | "APPROVED" | "REJECTED";
export type CreatorCategory = "CAFE" | "FOOD" | "TRAVEL" | "CULTURE" | "ETC";

export interface CreatorApplication {
  id: string;
  category: CreatorCategory;
  channelUrl: string;
  intro: string;
  plan: string;
  status: Exclude<CreatorStatus, "NONE">;
  submittedAt: string;
  reviewedAt: string | null;
  rejectReason: string | null;
}

export interface PayoutAccount {
  bank: string;
  accountNumberMasked: string;
  holder: string;
}

export interface CreatorOverview {
  status: CreatorStatus;
  application: CreatorApplication | null;
  history: CreatorApplication[];
  account: PayoutAccount | null;
}

export interface Earnings {
  summary: { totalSales: number; unsettled: number; scheduled: number; paidTotal: number; feeRate: number };
  sales: { id: string; collectionTitle: string; buyerUsername: string; amount: number; fee: number; net: number; status: "PAID" | "REFUNDED"; soldAt: string }[];
  settlements: { id: string; period: string; salesAmount: number; fee: number; amount: number; status: "SCHEDULED" | "PAID" | "HELD"; paidAt: string | null }[];
}

export interface ApplyInput {
  category: CreatorCategory;
  channelUrl: string;
  intro: string;
  plan: string;
}

const creatorKey = ["me", "creator"] as const;

export const useCreator = () => useQuery({ queryKey: creatorKey, queryFn: () => http.get<CreatorOverview>("/me/creator") });

export function useApplyCreator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ApplyInput) => http.post<CreatorApplication>("/me/creator/applications", input),
    onSuccess: () => qc.invalidateQueries({ queryKey: creatorKey }),
  });
}

export function useSavePayoutAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { bank: string; accountNumber: string; holder: string }) => http.put<PayoutAccount>("/me/creator/account", input),
    onSuccess: () => qc.invalidateQueries({ queryKey: creatorKey }),
  });
}

export const useEarnings = (enabled: boolean) =>
  useQuery({ queryKey: [...creatorKey, "earnings"], queryFn: () => http.get<Earnings>("/me/creator/earnings"), enabled });

/** 승인되면 프로필의 크리에이터 배지 · 유료 컬렉션 가격 설정이 바뀌므로 내 정보도 다시 받습니다. */
export const useRefreshMeOnApproval = () => {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: userKeys.me() });
};
