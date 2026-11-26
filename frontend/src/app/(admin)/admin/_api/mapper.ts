import type { AdminContent, AdminUser, Sanction } from "../_lib/models";
import type { AdminFeedDto, AdminUserDto, SanctionDto } from "./dto";

export function toSanction(dto: SanctionDto): Sanction {
  return { id: dto.id, type: dto.type, reason: dto.reason, startedAt: dto.startedAt, until: dto.until };
}

export function toAdminUser(dto: AdminUserDto): AdminUser {
  return {
    id: dto.id, username: dto.username, nickname: dto.nickname, profileImageUrl: dto.profileImageUrl,
    isCreator: dto.isCreator, reportCount: dto.reportCount, postCount: dto.postCount, status: dto.status,
    sanction: dto.sanction ? toSanction(dto.sanction) : null, sanctions: dto.sanctions.map(toSanction),
  };
}

export function toAdminContent(dto: AdminFeedDto): AdminContent {
  return {
    id: dto.id, type: dto.type, title: dto.title, author: dto.author ? { ...dto.author } : null,
    imageUrl: dto.imageUrl, visibility: dto.visibility, price: dto.price, hidden: dto.hidden,
    reportCount: dto.reportCount, createdAt: dto.createdAt, link: dto.link,
  };
}
