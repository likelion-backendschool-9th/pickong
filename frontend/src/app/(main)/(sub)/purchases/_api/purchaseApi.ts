import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

/* 구매 내역 · 환불 신청 */

export type PurchaseStatus = "PAID" | "REFUND_REQUESTED" | "REFUNDED" | "REFUND_REJECTED";

export interface Purchase {
  id: string;
  collection: { id: string; title: string; coverImageUrl: string | null; ownerUsername: string } | null;
  amount: number;
  method: "TEST" | "CARD";
  card: { company: string; installment: number } | null;
  purchasedAt: string;
  status: PurchaseStatus;
  refundDeadline: string;
  refundable: boolean;
  refund: { reason: string; detail: string; requestedAt: string; processedAt: string | null; rejectReason: string | null } | null;
}

const purchasesKey = ["me", "purchases"] as const;

export const usePurchases = () => useQuery({ queryKey: purchasesKey, queryFn: () => http.get<Purchase[]>("/me/purchases") });

export const usePurchase = (id: string) =>
  useQuery({ queryKey: [...purchasesKey, id], queryFn: () => http.get<Purchase>(`/me/purchases/${id}`) });

export function useRequestRefund(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { reason: string; detail: string }) => http.post<Purchase>(`/purchases/${id}/refunds`, input),
    onSuccess: () => qc.invalidateQueries({ queryKey: purchasesKey }),
  });
}
