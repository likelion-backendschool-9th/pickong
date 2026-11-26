import type { SanctionType, UserStatus } from "../_lib/models";
import type { Visibility } from "@/shared/config/visibility";

interface UserSummaryDto {
  id: string;
  username: string;
  nickname: string;
  profileImageUrl: string | null;
}

export interface SanctionDto {
  id: string;
  type: SanctionType;
  reason: string;
  startedAt: string;
  until: string | null;
}

export interface AdminUserDto extends UserSummaryDto {
  isCreator: boolean | null;
  reportCount: number;
  postCount: number;
  status: UserStatus;
  sanction: SanctionDto | null;
  sanctions: SanctionDto[];
}

export interface AdminFeedDto {
  id: string;
  type: "POST";
  title: string;
  author: UserSummaryDto | null;
  imageUrl: string | null;
  visibility: Visibility;
  price: number | null;
  hidden: boolean;
  reportCount: number;
  createdAt: string;
  link: string | null;
}
