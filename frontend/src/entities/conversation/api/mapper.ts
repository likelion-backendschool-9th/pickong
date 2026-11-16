import { toPlace } from "@/entities/place";
import { toUserSummary } from "@/entities/user";
import type { Conversation, Message, MessageAttachment } from "../model";
import type { ConversationDto, MessageAttachmentDto, MessageDto } from "./dto";

const toAttachment = (dto: MessageAttachmentDto | null): MessageAttachment | null => {
  if (!dto) return null;
  if (dto.type === "PLACE") return { type: "PLACE", place: dto.place ? toPlace(dto.place) : null };
  if (dto.type === "IMAGE") return { type: "IMAGE", url: dto.url };
  return { type: "POST", post: dto.post };
};

export const toMessage = (dto: MessageDto): Message => ({
  id: dto.id,
  conversationId: dto.conversationId,
  senderId: dto.senderId,
  isMine: dto.isMine,
  text: dto.text,
  attachment: toAttachment(dto.attachment),
  createdAt: dto.createdAt,
});

export const toConversation = (dto: ConversationDto): Conversation => ({
  id: dto.id,
  partner: toUserSummary(dto.partner),
  lastMessage: dto.lastMessage ? toMessage(dto.lastMessage) : null,
  unreadCount: dto.unreadCount,
  updatedAt: dto.updatedAt,
});
