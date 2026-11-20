import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "@/shared/api/http";
import type { Visibility } from "@/shared/config/visibility";

/*
 * 관리자 API. 실제 백엔드는 /api/v1/admin/**에서 관리자 계정 토큰만 받습니다 (common_blueprint.md 5.1).
 * 목록은 지금 전체를 한 번에 받고, 백엔드 연결 시 page · size 페이지네이션으로 바꿉니다 (5.6).
 */

export interface AdminUserSummary {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}

export type ReportTarget = "POST" | "COMMENT" | "USER" | "COLLECTION";
export type ReportAction = "DISMISS" | "HIDE_CONTENT" | "SANCTION_USER";
export type SanctionType = "WARNING" | "SUSPEND_7" | "SUSPEND_30" | "BAN";

export interface Report {
  id: string;
  targetType: ReportTarget;
  targetId: string;
  target: { text: string; imageUrl: string | null; link: string | null };
  reportedUser: AdminUserSummary | null;
  reporter: AdminUserSummary | null;
  reason: string;
  detail: string;
  createdAt: string;
  status: "PENDING" | "RESOLVED";
  resolution: { action: ReportAction; memo: string; resolvedAt: string } | null;
}

export interface Sanction {
  type: SanctionType;
  reason: string;
  startedAt: string;
  until: string | null;
}

export interface AdminUser extends AdminUserSummary {
  isCreator: boolean;
  reportCount: number;
  postCount: number;
  status: "ACTIVE" | "SUSPENDED" | "BANNED";
  sanction: Sanction | null;
}

export interface AdminContent {
  id: string;
  type: "POST" | "COLLECTION";
  title: string;
  author: AdminUserSummary | null;
  imageUrl: string | null;
  visibility: Visibility;
  price: number | null;
  hidden: boolean;
  reportCount: number;
  createdAt: string;
  link: string;
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

const adminKey = ["admin"] as const;

/** 처리하면 대시보드 숫자와 목록이 함께 바뀌므로 관리자 데이터 전체를 다시 받습니다. */
function useAdminMutation<T, R>(fn: (input: T) => Promise<R>) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => qc.invalidateQueries({ queryKey: adminKey }) });
}

export const useAdminSummary = () => useQuery({ queryKey: [...adminKey, "summary"], queryFn: () => http.get<AdminSummary>("/admin/summary") });

export const useReports = (status: Report["status"]) =>
  useQuery({ queryKey: [...adminKey, "reports", status], queryFn: () => http.get<Report[]>("/admin/reports", { query: { status } }) });

export const useResolveReport = () =>
  useAdminMutation(({ id, ...body }: { id: string; action: ReportAction; memo: string; sanctionType?: SanctionType }) =>
    http.post<Report>(`/admin/reports/${id}/resolve`, body),
  );

export const useAdminUsers = (q: string, filter: "ALL" | "REPORTED" | "SANCTIONED") =>
  useQuery({
    queryKey: [...adminKey, "users", q, filter],
    queryFn: () => http.get<AdminUser[]>("/admin/users", { query: { q, filter } }),
    placeholderData: (prev) => prev,
  });

export const useSanctionUser = () =>
  useAdminMutation(({ id, ...body }: { id: string; type: SanctionType; reason: string }) => http.post<AdminUser>(`/admin/users/${id}/sanctions`, body));

export const useLiftSanction = () => useAdminMutation((id: string) => http.delete<AdminUser>(`/admin/users/${id}/sanctions`));

export const useAdminContents = (type: AdminContent["type"], q: string) =>
  useQuery({
    queryKey: [...adminKey, "contents", type, q],
    queryFn: () => http.get<AdminContent[]>("/admin/contents", { query: { type, q } }),
    placeholderData: (prev) => prev,
  });

export const useSetContentHidden = () =>
  useAdminMutation(({ type, id, hidden }: { type: AdminContent["type"]; id: string; hidden: boolean }) =>
    http.patch<AdminContent>(`/admin/contents/${type.toLowerCase()}/${id}`, { hidden }),
  );

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
