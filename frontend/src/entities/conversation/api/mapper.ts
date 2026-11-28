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
  sender: dto.sender ? toUserSummary(dto.sender) : null,
  isMine: dto.isMine,
  type: dto.type,
  text: dto.text ?? "",
  system: dto.system,
  attachment: toAttachment(dto.attachment),
  createdAt: dto.createdAt,
  ...(dto.clientMessageId ? { clientMessageId: dto.clientMessageId } : {}),
});

export const toConversation = (dto: ConversationDto): Conversation => ({
  id: dto.id,
  type: dto.type,
  title: dto.title,
  partner: dto.partner ? toUserSummary(dto.partner) : null,
  members: dto.members.map((m) => ({ ...toUserSummary(m), isMe: m.isMe })),
  memberCount: dto.memberCount,
  lastMessage: dto.lastMessage ? toMessage(dto.lastMessage) : null,
  unreadCount: dto.unreadCount,
  muted: dto.muted,
  createdAt: dto.createdAt,
  updatedAt: dto.updatedAt,
});
