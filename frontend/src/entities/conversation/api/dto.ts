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
}

export interface ConversationDto {
  id: string;
  partner: UserSummaryDto;
  lastMessage: MessageDto | null;
  unreadCount: number;
  updatedAt: string;
}
