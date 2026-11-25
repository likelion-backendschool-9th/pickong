import { http } from "@/shared/api/http";
import type { AdminUserFilter, SanctionType } from "../_lib/models";
import type { AdminFeedDto, AdminUserDto, SanctionDto } from "./dto";
import { toAdminContent, toAdminUser, toSanction } from "./mapper";

export const moderationApi = {
  users: async (q: string, filter: AdminUserFilter) =>
    (await http.get<AdminUserDto[]>("/admin/users", { query: { q, filter } })).map(toAdminUser),
  sanction: async ({ id, ...body }: { id: string; type: SanctionType; reason: string }) =>
    toSanction(await http.post<SanctionDto>(`/admin/users/${encodeURIComponent(id)}/sanctions`, body)),
  revoke: ({ userId, sanctionId, reason }: { userId: string; sanctionId: string; reason?: string }) =>
    http.delete<void>(`/admin/users/${encodeURIComponent(userId)}/sanctions/${encodeURIComponent(sanctionId)}`,
      reason ? { reason } : undefined),
  feeds: async (q: string) => (await http.get<AdminFeedDto[]>("/admin/feeds", { query: { q } })).map(toAdminContent),
  setHidden: async ({ id, hidden, reason }: { id: string; hidden: boolean; reason?: string }) =>
    toAdminContent(await http.patch<AdminFeedDto>(`/admin/feeds/${encodeURIComponent(id)}`, { hidden, ...(reason ? { reason } : {}) })),
};
