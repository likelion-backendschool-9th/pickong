import type { Place } from "@/entities/place";
import type { UserSummary } from "@/entities/user";

/** 메시지에 붙은 공유 카드. 볼 수 없는 게시물 · 사라진 장소는 null */
export type MessageAttachment =
  | { type: "POST"; post: { id: string; imageUrl: string | null; authorUsername: string; content: string } | null }
  | { type: "PLACE"; place: Place | null }
  | { type: "IMAGE"; url: string };

/** 보낼 때 첨부 (사진은 URL, 장소는 id) */
export type OutgoingAttachment = { type: "IMAGE"; url: string } | { type: "PLACE"; placeId: string };

/** 그룹 안내 메시지 내용. 사람은 그 일이 있었던 때의 아이디입니다 */
export interface SystemEvent {
  event: "CREATED" | "INVITED" | "LEFT" | "RENAMED";
  actor: { id: string; username: string };
  /** 초대된 사람들 (CREATED · INVITED) */
  targets: { id: string; username: string }[];
  /** 바뀐 그룹 이름 (CREATED · RENAMED) */
  title: string | null;
}

export interface Message {
  /** 서버 id(숫자 문자열). 보내는 중인 메시지는 "pending:{clientMessageId}" */
  id: string;
  conversationId: string;
  senderId: string;
  /** 보낸 사람 (안내 메시지면 그 일을 한 사람). 보내는 중인 내 메시지는 null */
  sender: UserSummary | null;
  isMine: boolean;
  /** TEXT: 회원이 보낸 글 · SYSTEM: 그룹 만들기 · 초대 · 나가기 · 이름 변경 안내 */
  type: "TEXT" | "SYSTEM";
  text: string;
  system: SystemEvent | null;
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

export interface GroupMember extends UserSummary {
  isMe: boolean;
}

/** 대화방: 1:1(DIRECT)과 그룹(GROUP). 그룹은 방장이 없고 멤버 누구나 이름 변경 · 초대할 수 있습니다 */
export interface Conversation {
  id: string;
  type: "DIRECT" | "GROUP";
  /** 그룹 이름. 1:1은 null */
  title: string | null;
  /** 1:1 상대. 그룹은 null */
  partner: UserSummary | null;
  /** 그룹 참여자 (나 포함, 들어온 순). 1:1은 빈 목록 */
  members: GroupMember[];
  memberCount: number;
  lastMessage: Message | null;
  unreadCount: number;
  /** 내 알림 끄기 (알림 기능 전이라 값만 저장) */
  muted: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 그룹 인원 (나 포함 3~10명) · 이름 길이. 백엔드와 같은 규칙 */
export const GROUP_MIN_MEMBERS = 3;
export const GROUP_MAX_MEMBERS = 10;
export const GROUP_TITLE_MAX = 20;

/** 대화방 이름: 그룹은 이름, 1:1은 상대 아이디 */
export const conversationName = (c: Conversation): string => (c.type === "GROUP" ? (c.title ?? "") : (c.partner?.username ?? ""));

const norm = (s: string) => s.replace(/\s+/g, "").toLowerCase();

/** 대화 검색: 1:1은 상대 아이디 · 이름, 그룹은 이름 · 참여자 아이디, 그리고 마지막 메시지 (띄어쓰기 · 대소문자 무시) */
export function matchesConversation(c: Conversation, query: string): boolean {
  const q = norm(query);
  if (!q) return true;
  const who =
    c.type === "GROUP"
      ? (c.title ?? "") + c.members.map((m) => m.username).join("")
      : (c.partner?.nickname ?? "") + (c.partner?.username ?? "");
  return norm(who + messagePreview(c.lastMessage)).includes(q);
}

const names = (people: { username: string }[]) => people.map((p) => p.username).join(", ");

/** 안내 메시지 문구 */
export function systemText(e: SystemEvent): string {
  switch (e.event) {
    case "CREATED":
      return `${e.actor.username}님이 ${names(e.targets)}님과 채팅방을 만들었어요`;
    case "INVITED":
      return `${e.actor.username}님이 ${names(e.targets)}님을 초대했어요`;
    case "LEFT":
      return `${e.actor.username}님이 나갔어요`;
    case "RENAMED":
      return `${e.actor.username}님이 채팅방 이름을 ‘${e.title ?? ""}’(으)로 바꿨어요`;
  }
}

/** 대화 목록 미리보기 문구 */
export function messagePreview(m: Message | null): string {
  if (!m) return "";
  if (m.type === "SYSTEM" && m.system) return systemText(m.system);
  if (m.text) return m.text;
  if (m.attachment?.type === "POST") return "게시물을 공유했어요";
  if (m.attachment?.type === "PLACE") return "장소를 공유했어요";
  if (m.attachment?.type === "IMAGE") return "사진을 보냈어요";
  return "";
}

/** 그룹 이름 확인: 앞뒤 공백을 지우고 1~20자(이모지 한 개 = 1자) */
export function groupTitleError(title: string): string | null {
  const v = title.trim();
  if (!v) return "채팅방 이름을 입력해 주세요.";
  return [...v].length > GROUP_TITLE_MAX ? `채팅방 이름은 ${GROUP_TITLE_MAX}자까지 쓸 수 있어요.` : null;
}
