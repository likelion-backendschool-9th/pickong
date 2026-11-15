import { useQuery } from "@tanstack/react-query";
import { toUser, type User, type UserDto } from "@/entities/user";
import { http } from "@/shared/api/http";

export type NotificationType = "FOLLOW" | "COMMENT" | "LIKE" | "COLLECTION_SAVE" | "PLAN_CONFLICT";

export interface AppNotification {
  id: string;
  type: NotificationType;
  actor: User | null;
  message: string;
  detail: string;
  /** 알림 유형에 맞는 이동 경로 (프로필 · 게시물 · 컬렉션 · 충돌이 생긴 일정) */
  link: string;
  createdAt: string;
  read: boolean;
}

interface NotificationDto extends Omit<AppNotification, "actor"> {
  actor: UserDto | null;
}

export const useNotifications = () =>
  useQuery({
    queryKey: ["notifications"],
    queryFn: async () =>
      (await http.get<NotificationDto[]>("/notifications")).map((n) => ({ ...n, actor: n.actor ? toUser(n.actor) : null })),
  });
