import { useMutation, useQueryClient } from "@tanstack/react-query";
import { collectionKeys } from "@/entities/collection";
import { http } from "@/shared/api/http";

/*
 * 카드 결제. 실제로는 ① 서버에서 주문 생성 → ② PG 결제창에서 카드 인증 → ③ 서버가 PG 승인 API로 확인 순서입니다.
 * 카드 번호는 PG 결제창에서만 입력받고 찜콩 서버 · 화면은 다루지 않습니다. 지금은 ②를 mock 결제창으로 대신합니다.
 */

export interface PaymentOrder {
  orderId: string;
  collectionId: string;
  amount: number;
  orderName: string;
  sellerNickname: string;
  cardCompanies: string[];
}

export interface ConfirmInput {
  orderId: string;
  amount: number;
  cardCompany: string;
  installment: number;
}

export const useCreateOrder = () =>
  useMutation({ mutationFn: (collectionId: string) => http.post<PaymentOrder>("/payments/orders", { collectionId }) });

export function useConfirmPayment(collectionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ConfirmInput) => http.post<{ id: string }>("/payments/confirm", input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: collectionKeys.detail(collectionId) });
      qc.invalidateQueries({ queryKey: collectionKeys.all });
      qc.invalidateQueries({ queryKey: ["me", "purchases"] });
    },
  });
}
