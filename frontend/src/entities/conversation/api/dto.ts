import type { PlaceDto } from "@/entities/place";
import type { UserSummaryDto } from "@/entities/user";

export type MessageAttachmentDto =
  | { type: "POST"; post: { id: string; imageUrl: string | null; authorUsername: string; content: string } | null }
  | { type: "PLACE"; place: PlaceDto | null }
  | { type: "IMAGE"; url: string };

export interface SystemEventDto {
  event: "CREATED" | "INVITED" | "LEFT" | "RENAMED";
  actor: { id: string; username: string };
  targets: { id: string; username: string }[];
  title: string | null;
}

export interface MessageDto {
  id: string;
  conversationId: string;
  senderId: string;
  sender: UserSummaryDto | null;
  isMine: boolean;
  type: "TEXT" | "SYSTEM";
  /** TEXT의 본문. SYSTEM이면 null */
  text: string | null;
  system: SystemEventDto | null;
  attachment: MessageAttachmentDto | null;
  createdAt: string;
  /** 실시간 이벤트(MESSAGE_CREATED)에만 있음 */
  clientMessageId?: string;
}

/** GET /conversations/{id}/messages — items는 오래된 순. nextCursor는 cursor면 더 오래된 쪽, after면 다음 after 값 */
export interface MessagePageDto {
  items: MessageDto[];
  nextCursor: string | null;
}

/** /user/queue/dm 이벤트 (common_blueprint 5.9). 모르는 type은 무시합니다 */
export type DmEventDto =
  | { type: "MESSAGE_CREATED"; data: MessageDto }
  | { type: "READ_UPDATED"; data: { conversationId: string; userId: string; lastReadMessageId: string } }
  | { type: "CONVERSATION_UPDATED"; data: { conversationId: string } }
  | { type: string; data: unknown };

/** /user/queue/errors */
export interface StompErrorDto {
  status: number;
  code: string;
  message: string;
  clientMessageId: string | null;
}

export interface ConversationDto {
  id: string;
  type: "DIRECT" | "GROUP";
  title: string | null;
  /** 1:1 상대. 그룹은 null */
  partner: UserSummaryDto | null;
  /** 그룹 참여자 (나 포함). 1:1은 빈 목록 */
  members: (UserSummaryDto & { isMe: boolean })[];
  memberCount: number;
  lastMessage: MessageDto | null;
  unreadCount: number;
  muted: boolean;
  createdAt: string;
  updatedAt: string;
}
