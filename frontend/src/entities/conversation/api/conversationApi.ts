import { http } from "@/shared/api/http";
import type { OutgoingAttachment } from "../model";
import type { ConversationDto, MessageDto } from "./dto";
import { toConversation, toMessage } from "./mapper";

export const conversationApi = {
  list: async () => (await http.get<ConversationDto[]>("/conversations")).map(toConversation),
  get: async (id: string) => toConversation(await http.get<ConversationDto>(`/conversations/${id}`)),
  /** 상대와의 대화방을 열거나 새로 만듭니다. */
  start: async (username: string) => toConversation(await http.post<ConversationDto>("/conversations", { username })),
  /** 메시지 목록 (오래된 순). 불러오면 서버가 읽음 처리합니다. */
  messages: async (id: string) => (await http.get<MessageDto[]>(`/conversations/${id}/messages`)).map(toMessage),
  /** 글 · 첨부 중 하나는 있어야 합니다. */
  send: async (id: string, input: { text: string; attachment?: OutgoingAttachment | null }) =>
    toMessage(await http.post<MessageDto>(`/conversations/${id}/messages`, { text: input.text, attachment: input.attachment ?? null })),
};
