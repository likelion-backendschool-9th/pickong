import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { http } from "@/shared/api/http";
import type { AdminContent, AdminUserFilter, AdminUserSummary, SanctionType } from "../_lib/models";
import { adminKey, invalidateModerationQueries } from "./cache";
import { moderationApi } from "./moderationApi";

/*
 * 관리자 API. 실제 백엔드는 /api/v1/admin/**에서 관리자 계정 토큰만 받습니다 (common_blueprint.md 5.1).
 * 확정된 목록 계약은 페이지네이션 없는 배열입니다.
 */

export type ReportTarget = "POST" | "COMMENT" | "USER" | "COLLECTION";
export type ReportAction = "DISMISS" | "HIDE_CONTENT" | "SANCTION_USER";

export interface Report {
  id: string;
  targetType: ReportTarget;
  targetId: string;
  target: {
    text: string;
    imageUrl: string | null;
    link: string | null;
    snapshot: { text: string | null; imageUrl: string | null } | null;
    current: { text: string | null; imageUrl: string | null } | null;
    modified: boolean | null;
    visibility: "LIVE" | "HIDDEN" | "DELETED" | "UNAVAILABLE";
  };
  reportedUser: AdminUserSummary | null;
  reporter: AdminUserSummary | null;
  reason: string;
  detail: string;
  createdAt: string;
  status: "PENDING" | "RESOLVED";
  resolution: { action: ReportAction; memo: string; resolvedAt: string } | null;
}

export interface CreatorApplicationReview {
  id: string;
  applicant: AdminUserSummary | null;
  category: string;
  channelUrl: string;
  intro: string;
  plan: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  submittedAt: string;
  reviewedAt: string | null;
  rejectReason: string | null;
}

export interface RefundRequest {
  id: string;
  collection: { id: string; title: string; coverImageUrl: string | null; ownerNickname: string } | null;
  buyer: { username: string; nickname: string } | null;
  amount: number;
  method: "TEST" | "CARD";
  card: { company: string; installment: number } | null;
  purchasedAt: string;
  status: "PAID" | "REFUND_REQUESTED" | "REFUNDED" | "REFUND_REJECTED";
  refund: { reason: string; detail: string; requestedAt: string; processedAt: string | null; rejectReason: string | null } | null;
}

export interface Settlement {
  id: string;
  period: string;
  salesAmount: number;
  fee: number;
  amount: number;
  status: "SCHEDULED" | "PAID" | "HELD";
  paidAt: string | null;
  creator: AdminUserSummary | null;
  account: { bank: string; accountNumberMasked: string; holder: string } | null;
}

export interface AdminSummary {
  pendingReports: number;
  pendingCreatorApplications: number;
  pendingRefunds: number;
  scheduledSettlements: number;
  sanctionedUsers: number;
  hiddenContents: number;
}

/** 처리하면 대시보드 숫자와 목록이 함께 바뀌므로 관리자 데이터 전체를 다시 받습니다. */
function useAdminMutation<T, R>(fn: (input: T) => Promise<R>, moderation = false) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => moderation ? invalidateModerationQueries(qc) : qc.invalidateQueries({ queryKey: adminKey }),
    onError: (error) => {
      if (moderation && error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        return invalidateModerationQueries(qc);
      }
    },
  });
}

export const useAdminSummary = () => useQuery({ queryKey: [...adminKey, "summary"], queryFn: () => http.get<AdminSummary>("/admin/summary") });

export const useReports = (status: Report["status"]) =>
  useQuery({ queryKey: [...adminKey, "reports", status], queryFn: () => http.get<Report[]>("/admin/reports", { query: { status } }) });

export const useResolveReport = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; action: ReportAction; memo: string; sanctionType?: SanctionType }) =>
      http.post<Report>(`/admin/reports/${id}/resolve`, body),
    onSuccess: () => invalidateModerationQueries(qc),
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) {
        return invalidateModerationQueries(qc);
      }
    },
  });
};

export const useAdminUsers = (q: string, filter: AdminUserFilter) =>
  useQuery({
    queryKey: [...adminKey, "users", q, filter],
    queryFn: () => moderationApi.users(q, filter),
    placeholderData: (prev) => prev,
  });

export const useSanctionUser = () =>
  useAdminMutation(moderationApi.sanction, true);

export const useLiftSanction = () => useAdminMutation(moderationApi.revoke, true);

export const useAdminContents = (type: AdminContent["type"], q: string) =>
  useQuery({
    queryKey: [...adminKey, "contents", type, q],
    queryFn: () => moderationApi.feeds(q),
    enabled: type === "POST",
  });

export const useSetContentHidden = () =>
  useAdminMutation(moderationApi.setHidden, true);

export const useCreatorApplications = (status: "PENDING" | "REVIEWED") =>
  useQuery({
    queryKey: [...adminKey, "creator-applications", status],
    queryFn: () => http.get<CreatorApplicationReview[]>("/admin/creator-applications", { query: { status } }),
  });

export const useReviewCreator = () =>
  useAdminMutation(({ id, ...body }: { id: string; approve: boolean; rejectReason: string }) =>
    http.post<CreatorApplicationReview>(`/admin/creator-applications/${id}/review`, body),
  );

export const useRefundRequests = (status: "PENDING" | "DONE") =>
  useQuery({ queryKey: [...adminKey, "refunds", status], queryFn: () => http.get<RefundRequest[]>("/admin/refunds", { query: { status } }) });

export const useProcessRefund = () =>
  useAdminMutation(({ id, ...body }: { id: string; approve: boolean; rejectReason: string }) => http.post<RefundRequest>(`/admin/refunds/${id}`, body));

export const useSettlements = (status: "SCHEDULED" | "PAID") =>
  useQuery({ queryKey: [...adminKey, "settlements", status], queryFn: () => http.get<Settlement[]>("/admin/settlements", { query: { status } }) });

export const useSettlementAction = () =>
  useAdminMutation(({ id, action }: { id: string; action: "PAY" | "HOLD" | "RELEASE" }) => http.post<Settlement>(`/admin/settlements/${id}`, { action }));
