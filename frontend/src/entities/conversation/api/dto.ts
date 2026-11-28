import type { PlaceDto } from "@/entities/place";
import type { UserSummaryDto } from "@/entities/user";

export type MessageAttachmentDto =
  | { type: "POST"; post: { id: string; imageUrl: string | null; authorUsername: string; content: string } | null }
  | { type: "PLACE"; place: PlaceDto | null }
  | { type: "IMAGE"; url: string };

export interface MessageDto {
  id: string;
  conversationId: string;
  senderId: string;
  isMine: boolean;
  text: string;
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
  partner: UserSummaryDto;
  lastMessage: MessageDto | null;
  unreadCount: number;
  updatedAt: string;
}
