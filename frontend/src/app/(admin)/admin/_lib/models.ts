import type { Visibility } from "@/shared/config/visibility";

export interface AdminUserSummary {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}

export type SanctionType = "WARNING" | "SUSPEND_7" | "SUSPEND_30" | "BAN";
export type UserStatus = "ACTIVE" | "SUSPENDED" | "BANNED" | "WITHDRAWN_PENDING" | "WITHDRAWN";
export type AdminUserFilter = "ALL" | "REPORTED" | "SANCTIONED";

export interface Sanction {
  id: string;
  type: SanctionType;
  reason: string;
  startedAt: string;
  until: string | null;
}

export interface AdminUser extends AdminUserSummary {
  isCreator: boolean | null;
  reportCount: number;
  postCount: number;
  status: UserStatus;
  sanction: Sanction | null;
  sanctions: Sanction[];
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
  link: string | null;
}
