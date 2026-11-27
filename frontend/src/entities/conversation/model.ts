import type { Place } from "@/entities/place";
import type { UserSummary } from "@/entities/user";

/** 메시지에 붙은 공유 카드. 볼 수 없는 게시물 · 사라진 장소는 null */
export type MessageAttachment =
  | { type: "POST"; post: { id: string; imageUrl: string | null; authorUsername: string; content: string } | null }
  | { type: "PLACE"; place: Place | null }
  | { type: "IMAGE"; url: string };

/** 보낼 때 첨부 (사진은 URL, 장소는 id) */
export type OutgoingAttachment = { type: "IMAGE"; url: string } | { type: "PLACE"; placeId: string };

export interface Message {
  /** 서버 id(숫자 문자열). 보내는 중인 메시지는 "pending:{clientMessageId}" */
  id: string;
  conversationId: string;
  senderId: string;
  isMine: boolean;
  text: string;
  attachment: MessageAttachment | null;
  createdAt: string;
  /** 내가 보낸 메시지를 만든 UUID. 서버 확인(MESSAGE_CREATED)과 보내는 중인 말풍선을 짝지을 때 씁니다 */
  clientMessageId?: string;
  /** 보내는 중 · 실패. 서버에 저장된 메시지는 없음(undefined) */
  status?: "sending" | "failed";
}

/** 대화방 메시지 목록 (오래된 순) + 더 오래된 내역을 불러올 커서 */
export interface MessageThread {
  items: Message[];
  olderCursor: string | null;
}

/** 1:1 대화방 */
export interface Conversation {
  id: string;
  partner: UserSummary;
  lastMessage: Message | null;
  unreadCount: number;
  updatedAt: string;
}

const norm = (s: string) => s.replace(/\s+/g, "").toLowerCase();

/** 대화 검색: 상대 아이디 · 이름 · 마지막 메시지 (띄어쓰기 · 대소문자 무시). 화면에는 아이디만 보이지만 이름으로도 찾을 수 있게 둡니다 */
export function matchesConversation(c: Conversation, query: string): boolean {
  const q = norm(query);
  if (!q) return true;
  return norm(c.partner.nickname + c.partner.username + messagePreview(c.lastMessage)).includes(q);
}

/** 대화 목록 미리보기 문구 */
export function messagePreview(m: Message | null): string {
  if (!m) return "";
  if (m.text) return m.text;
  if (m.attachment?.type === "POST") return "게시물을 공유했어요";
  if (m.attachment?.type === "PLACE") return "장소를 공유했어요";
  if (m.attachment?.type === "IMAGE") return "사진을 보냈어요";
  return "";
}
