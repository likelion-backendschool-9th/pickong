import type { PurchaseStatus } from "../_api/purchaseApi";

export const REFUND_REASONS = [
  { value: "WRONG_PURCHASE", label: "실수로 잘못 샀어요" },
  { value: "NOT_AS_DESCRIBED", label: "소개와 내용이 달라요" },
  { value: "CLOSED_PLACES", label: "문을 닫은 장소가 많아요" },
  { value: "DUPLICATE", label: "같은 컬렉션을 두 번 결제했어요" },
  { value: "ETC", label: "기타" },
] as const;

export const refundReasonLabel = (value: string) => REFUND_REASONS.find((r) => r.value === value)?.label ?? value;

export const PURCHASE_STATUS: Record<PurchaseStatus, { label: string; tone: "lime" | "neutral" | "warning" | "danger" }> = {
  PAID: { label: "결제 완료", tone: "lime" },
  REFUND_REQUESTED: { label: "환불 심사 중", tone: "warning" },
  REFUNDED: { label: "환불 완료", tone: "neutral" },
  REFUND_REJECTED: { label: "환불 거절", tone: "danger" },
};

/** 환불 신청 마감까지 남은 날 (마감이 지났으면 0, 하루가 안 남았으면 1) */
export function refundDaysLeft(deadlineIso: string, now: number = Date.now()): number {
  const ms = new Date(deadlineIso).getTime() - now;
  return ms <= 0 ? 0 : Math.ceil(ms / 86_400_000);
}
