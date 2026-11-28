import { http } from "@/shared/api/http";
import type { Message } from "../model";
import type { ConversationDto, MessagePageDto } from "./dto";
import { toConversation, toMessage } from "./mapper";

/** 한 번에 불러오는 메시지 수 (서버 최대 100) */
export const MESSAGE_PAGE_SIZE = 50;

export interface MessagePage {
  items: Message[];
  nextCursor: string | null;
}

const toPage = (dto: MessagePageDto): MessagePage => ({ items: dto.items.map(toMessage), nextCursor: dto.nextCursor });

/**
 * 대화 HTTP API (backend_blueprint 3장 conversation). 메시지 보내기는 HTTP가 아니라 STOMP입니다(./realtime).
 */
export const conversationApi = {
  list: async () => (await http.get<ConversationDto[]>("/conversations")).map(toConversation),
  unreadCount: async () => (await http.get<{ count: number }>("/conversations/unread-count")).count,
  get: async (id: string) => toConversation(await http.get<ConversationDto>(`/conversations/${id}`)),
  /** 상대와의 1:1 대화방을 열거나(있으면 그대로) 새로 만듭니다. */
  start: async (username: string) => toConversation(await http.post<ConversationDto>("/conversations/direct", { username })),
  /** 최신 메시지부터 (cursor가 있으면 그보다 오래된 메시지). 읽음 처리는 하지 않습니다. */
  messages: async (id: string, cursor?: string | null) =>
    toPage(await http.get<MessagePageDto>(`/conversations/${id}/messages`, { query: { cursor, size: MESSAGE_PAGE_SIZE } })),
  /** after보다 새 메시지 (끊긴 동안 빠진 메시지 보충). nextCursor가 있으면 그 값을 다시 after로 */
  messagesAfter: async (id: string, after: string) =>
    toPage(await http.get<MessagePageDto>(`/conversations/${id}/messages`, { query: { after, size: MESSAGE_PAGE_SIZE } })),
  /** 여기까지 읽음. 뒤로 가지 않습니다. */
  markRead: (id: string, lastReadMessageId: string) => http.patch<void>(`/conversations/${id}/read`, { lastReadMessageId }),
  /** 그룹 만들기: 나 포함 3~10명, 초대는 아이디로 */
  createGroup: async (input: { title: string; usernames: string[] }) =>
    toConversation(await http.post<ConversationDto>("/conversations/group", input)),
  /** 그룹 이름(멤버 누구나) · 내 알림 끄기. 보낸 항목만 바뀝니다 */
  update: async (id: string, input: { title?: string; muted?: boolean }) =>
    toConversation(await http.patch<ConversationDto>(`/conversations/${id}`, input)),
  /** 그룹 초대(멤버 누구나). 이미 참여 중인 사람은 건너뜁니다 */
  invite: async (id: string, usernames: string[]) =>
    toConversation(await http.post<ConversationDto>(`/conversations/${id}/members`, { usernames })),
  leave: (id: string) => http.delete<void>(`/conversations/${id}/members/me`),
};
