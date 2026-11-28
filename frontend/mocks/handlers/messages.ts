import { canView, db, findPlace, findUser, findUserByName, ME_ID, nextId, placeDto } from "../db.ts";
import type { ConversationRow, MessageRow, SystemEventRow } from "../fixtures/types.ts";
import { HttpError, notFound, RawResponse, type Route } from "../router.ts";

/*
 * DM · 그룹 채팅. 백엔드와 같은 계약입니다 (backend_blueprint 3장 conversation, common_blueprint 5.9).
 * - HTTP: 목록(1:1 · 그룹 섞어 최근 순) · 안 읽은 수 · 1:1 열기 · 그룹 만들기 · 상세 · 이름 변경 · 알림 끄기 · 초대 · 나가기 ·
 *   메시지 커서 페이지 · 읽음. 메시지 보내기 HTTP API는 없습니다.
 * - 보내기 · 실시간은 STOMP(mocks/realtime.ts)가 이 파일의 sendMessage · markRead를 부릅니다.
 * - 그룹: 방장 없음, 멤버 누구나 이름 변경 · 초대, 내보내기 없음, 나 포함 3~10명. 초대된 사람은 이전 대화도 봄.
 *   만들기 · 초대 · 나가기 · 이름 변경은 안내 메시지(SYSTEM)로 남고 안 읽은 수에는 세지 않습니다.
 * - 메시지 id는 숫자 문자열이라 id 순서가 곧 메시지 순서입니다.
 */

const GROUP_MIN = 3;
const GROUP_MAX = 10;
const TITLE_MAX = 20;
const MAX_TEXT = 2_000;

/** 내가 참여 중인 대화방만 */
const conversationOr404 = (id: string) => {
  const c = db.conversations.find((x) => x.id === id && x.memberIds.includes(ME_ID));
  if (!c) throw new HttpError(404, "CONVERSATION_NOT_FOUND", "대화를 찾을 수 없어요.");
  return c;
};

const userSummary = (id: string) => {
  const u = findUser(id)!;
  return { id: u.id, username: u.username, nickname: u.nickname, profileImageUrl: u.profileImageUrl };
};

/** 공유 카드: 볼 수 없는 게시물(비공개 등)은 post: null */
function attachmentDto(m: MessageRow) {
  if (!m.attachment) return null;
  if (m.attachment.type === "IMAGE") return { type: "IMAGE" as const, url: m.attachment.url };
  if (m.attachment.type === "PLACE") {
    const place = findPlace(m.attachment.refId);
    return { type: "PLACE" as const, place: place ? placeDto(place) : null };
  }
  const refId = m.attachment.refId;
  const post = db.posts.find((p) => p.id === refId);
  if (!post || !canView(post.authorId, post.visibility)) return { type: "POST" as const, post: null };
  const author = findUser(post.authorId)!;
  return {
    type: "POST" as const,
    post: { id: post.id, imageUrl: post.photos[0]?.url ?? null, authorUsername: author.username, content: post.content },
  };
}

export function messageDto(m: MessageRow) {
  const system = m.type === "SYSTEM";
  return {
    id: m.id,
    conversationId: m.conversationId,
    senderId: m.senderId,
    sender: userSummary(m.senderId),
    isMine: m.senderId === ME_ID,
    type: system ? "SYSTEM" : "TEXT",
    text: system ? null : m.text,
    system: system ? (m.system ?? null) : null,
    attachment: attachmentDto(m),
    createdAt: m.createdAt,
  };
}

const byId = (a: MessageRow, b: MessageRow) => Number(a.id) - Number(b.id);

const messagesOf = (conversationId: string) => db.messages.filter((m) => m.conversationId === conversationId).sort(byId);

function conversationDto(c: ConversationRow) {
  const list = messagesOf(c.id);
  const last = list.at(-1) ?? null;
  const readAt = c.lastReadAt[ME_ID] ?? "";
  const group = c.type === "GROUP";
  return {
    id: c.id,
    type: c.type,
    title: group ? (c.title ?? "") : null,
    partner: group ? null : userSummary(c.memberIds.find((id) => id !== ME_ID)!),
    members: group ? c.memberIds.map((id) => ({ ...userSummary(id), isMe: id === ME_ID })) : [],
    memberCount: group ? c.memberIds.length : 2,
    lastMessage: last ? messageDto(last) : null,
    unreadCount: list.filter((m) => m.type !== "SYSTEM" && m.senderId !== ME_ID && m.createdAt > readAt).length,
    muted: (c.mutedBy ?? []).includes(ME_ID),
    createdAt: c.createdAt,
    updatedAt: last?.createdAt ?? (readAt || c.createdAt),
  };
}

let messageSeq = 1_000;
/** 같은 clientMessageId로 다시 보내면 저장하지 않습니다 (백엔드 UNIQUE(sender_id, client_message_id)) */
const sentByClientId = new Map<string, string>();

/** HTTP로 남긴 안내 메시지를 STOMP 연결에 보낼 곳 (mocks/realtime.ts가 등록) */
export const systemListeners: Array<(message: MessageRow) => void> = [];

const person = (id: string) => ({ id, username: findUser(id)!.username });

/** 그룹 안내 메시지(SYSTEM)를 남깁니다. 안 읽은 수에는 세지 않습니다. */
function announce(c: ConversationRow, system: SystemEventRow) {
  const now = new Date().toISOString();
  const m: MessageRow = { id: String(++messageSeq), conversationId: c.id, senderId: ME_ID, type: "SYSTEM", text: "", system, attachment: null, createdAt: now };
  db.messages.push(m);
  c.lastReadAt[ME_ID] = now;
  systemListeners.forEach((listen) => listen(m));
}

function validTitle(raw: unknown) {
  const title = String(raw ?? "").trim();
  if (!title || [...title].length > TITLE_MAX) throw new HttpError(400, "INVALID_GROUP_TITLE", "채팅방 이름은 1~20자로 입력해 주세요.");
  return title;
}

/** 아이디 → 회원 id (없으면 404, 나 · 중복은 뺌) */
function resolveUsernames(raw: unknown): string[] {
  if (!Array.isArray(raw)) throw new HttpError(400, "INVALID_INPUT", "초대할 사람을 골라 주세요.");
  const ids: string[] = [];
  for (const name of raw) {
    const username = String(name ?? "").trim().toLowerCase();
    if (!username) continue;
    const user = db.users.find((u) => u.username.toLowerCase() === username);
    if (!user) throw new HttpError(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다");
    if (user.id !== ME_ID && !ids.includes(user.id)) ids.push(user.id);
  }
  return ids;
}

/**
 * 메시지 보내기 (STOMP /app/dm.send). 백엔드와 같은 규칙 · 오류 코드입니다.
 * @returns 저장한(또는 이미 있던) 메시지와, 이번에 새로 저장했는지
 */
export function sendMessage(body: { conversationId?: unknown; clientMessageId?: unknown; text?: unknown; attachment?: unknown }) {
  const clientMessageId = typeof body.clientMessageId === "string" ? body.clientMessageId : "";
  if (!/^[0-9a-f-]{36}$/i.test(clientMessageId)) throw new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요.");
  if (body.attachment != null) throw new HttpError(400, "ATTACHMENT_NOT_SUPPORTED", "아직 사진이나 장소는 보낼 수 없어요.");
  const text = typeof body.text === "string" ? body.text : "";
  if (!text.trim()) throw new HttpError(400, "EMPTY_MESSAGE", "메시지를 입력해 주세요.");
  if ([...text].length > MAX_TEXT) throw new HttpError(400, "MESSAGE_TOO_LONG", "메시지는 2,000자까지 보낼 수 있어요.");
  const c = conversationOr404(String(body.conversationId ?? ""));

  const existing = sentByClientId.get(clientMessageId);
  if (existing) {
    const m = db.messages.find((x) => x.id === existing)!;
    if (m.conversationId !== c.id) throw new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요.");
    return { message: m, created: false };
  }
  const now = new Date().toISOString();
  const m: MessageRow = { id: String(++messageSeq), conversationId: c.id, senderId: ME_ID, text, attachment: null, createdAt: now };
  db.messages.push(m);
  sentByClientId.set(clientMessageId, m.id);
  c.lastReadAt[ME_ID] = now;
  return { message: m, created: true };
}

/**
 * 읽음 (HTTP PATCH /read · STOMP /app/dm.read). 뒤로 가지 않습니다.
 * @returns 실제로 옮긴 마지막 읽은 메시지 id, 바뀌지 않았으면 null
 */
export function markRead(conversationId: string, lastReadMessageId?: unknown): string | null {
  const c = conversationOr404(conversationId);
  const list = messagesOf(c.id);
  const target = lastReadMessageId == null ? list.at(-1) : (list.find((m) => m.id === String(lastReadMessageId)) ?? notFoundMessage());
  if (!target) return null;
  const readAt = c.lastReadAt[ME_ID] ?? "";
  if (target.createdAt <= readAt) return null;
  c.lastReadAt[ME_ID] = target.createdAt;
  return target.id;
}

const notFoundMessage = (): never => {
  throw new HttpError(404, "MESSAGE_NOT_FOUND", "메시지를 찾을 수 없어요.");
};

/** 메시지 커서 페이지: 기본 · cursor는 최신 쪽에서, after는 그 뒤부터. items는 항상 오래된 순 */
function messagePage(conversationId: string, query: URLSearchParams) {
  const cursor = query.get("cursor");
  const after = query.get("after");
  if (cursor && after) throw new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요.");
  const size = Math.min(Math.max(Number(query.get("size") ?? 30) || 30, 1), 100);
  const list = messagesOf(conversationId);
  if (after) {
    const newer = list.filter((m) => Number(m.id) > Number(after));
    const items = newer.slice(0, size);
    return { items: items.map(messageDto), nextCursor: newer.length > size ? items.at(-1)!.id : null };
  }
  const older = cursor ? list.filter((m) => Number(m.id) < Number(cursor)) : list;
  const items = older.slice(-size);
  return { items: items.map(messageDto), nextCursor: older.length > size ? items[0].id : null };
}

const created = (data: unknown) => new RawResponse(201, { "Content-Type": "application/json; charset=utf-8" }, JSON.stringify(data));

const notAGroup = () => new HttpError(400, "NOT_A_GROUP", "그룹 채팅에서만 할 수 있어요.");
const groupFull = () => new HttpError(409, "GROUP_FULL", "그룹 채팅은 10명까지 참여할 수 있어요.");

export const messageRoutes: Route[] = [
  {
    /** 내 대화 목록 (1:1 · 그룹, 최근 메시지 순). 메시지가 없는 대화방은 빼고 보여 줍니다. */
    method: "GET",
    path: "/api/v1/conversations",
    handler: () =>
      db.conversations
        .filter((c) => c.memberIds.includes(ME_ID) && messagesOf(c.id).length > 0)
        .map(conversationDto)
        // 백엔드는 마지막 메시지 id(= 시간 순) 내림차순. mock 예시 데이터는 id가 시간 순이 아니라 시각으로 정렬합니다
        .sort((a, b) => b.lastMessage!.createdAt.localeCompare(a.lastMessage!.createdAt)),
  },
  {
    /** 안 읽은 메시지 수 (하단바 배지, 안내 메시지는 세지 않음) */
    method: "GET",
    path: "/api/v1/conversations/unread-count",
    handler: () => ({
      count: db.conversations.filter((c) => c.memberIds.includes(ME_ID)).reduce((sum, c) => sum + conversationDto(c).unreadCount, 0),
    }),
  },
  {
    /** 1:1 대화방 열기: 있으면 200으로 그대로, 없으면 201로 새로 만듭니다. */
    method: "POST",
    path: "/api/v1/conversations/direct",
    handler: ({ body }) => {
      const username = String(body?.username ?? "").trim();
      if (!username) throw new HttpError(400, "INVALID_INPUT", "대화할 상대를 골라 주세요.");
      const partner = findUserByName(username.toLowerCase()) ?? findUserByName(username) ?? notFound("사용자");
      if (partner.id === ME_ID) throw new HttpError(400, "SELF_CONVERSATION_NOT_ALLOWED", "나에게는 메시지를 보낼 수 없어요.");
      const existing = db.conversations.find((x) => x.type === "DIRECT" && x.memberIds.includes(ME_ID) && x.memberIds.includes(partner.id));
      if (existing) return conversationDto(existing);
      const c: ConversationRow = { id: nextId("dm"), type: "DIRECT", memberIds: [ME_ID, partner.id], lastReadAt: {}, createdAt: new Date().toISOString() };
      db.conversations.push(c);
      return created(conversationDto(c));
    },
  },
  {
    /** 그룹 만들기: 나 포함 3~10명, '만들기' 안내 메시지 */
    method: "POST",
    path: "/api/v1/conversations/group",
    handler: ({ body }) => {
      const title = validTitle(body?.title);
      const invitees = resolveUsernames(body?.usernames);
      if (invitees.length + 1 < GROUP_MIN) throw new HttpError(400, "GROUP_TOO_SMALL", "그룹 채팅은 나를 포함해 3명 이상이어야 해요.");
      if (invitees.length + 1 > GROUP_MAX) throw groupFull();
      const c: ConversationRow = { id: nextId("g"), type: "GROUP", title, memberIds: [ME_ID, ...invitees], lastReadAt: {}, createdAt: new Date().toISOString() };
      db.conversations.push(c);
      announce(c, { event: "CREATED", actor: person(ME_ID), targets: invitees.map(person), title });
      return created(conversationDto(c));
    },
  },
  { method: "GET", path: "/api/v1/conversations/:id", handler: ({ params }) => conversationDto(conversationOr404(params.id)) },
  {
    /** 그룹 이름(멤버 누구나, 바뀔 때만 안내) · 내 알림 끄기 */
    method: "PATCH",
    path: "/api/v1/conversations/:id",
    handler: ({ params, body }) => {
      const c = conversationOr404(params.id);
      if (body?.title !== undefined) {
        if (c.type !== "GROUP") throw notAGroup();
        const title = validTitle(body.title);
        if (title !== c.title) {
          c.title = title;
          announce(c, { event: "RENAMED", actor: person(ME_ID), targets: [], title });
        }
      }
      if (typeof body?.muted === "boolean") {
        const muted = new Set(c.mutedBy ?? []);
        if (body.muted) muted.add(ME_ID);
        else muted.delete(ME_ID);
        c.mutedBy = [...muted];
      }
      return conversationDto(c);
    },
  },
  {
    /** 그룹 초대(멤버 누구나): 이미 참여 중인 사람은 건너뜀, 10명 초과면 409 */
    method: "POST",
    path: "/api/v1/conversations/:id/members",
    handler: ({ params, body }) => {
      const c = conversationOr404(params.id);
      if (c.type !== "GROUP") throw notAGroup();
      const joining = resolveUsernames(body?.usernames).filter((id) => !c.memberIds.includes(id));
      if (joining.length === 0) return conversationDto(c);
      if (c.memberIds.length + joining.length > GROUP_MAX) throw groupFull();
      c.memberIds.push(...joining);
      announce(c, { event: "INVITED", actor: person(ME_ID), targets: joining.map(person), title: null });
      return conversationDto(c);
    },
  },
  {
    /** 나가기. 그룹이면 '나가기' 안내 메시지 (mock은 나만 있어 그 안내를 볼 사람이 없음) */
    method: "DELETE",
    path: "/api/v1/conversations/:id/members/me",
    handler: ({ params }) => {
      const c = conversationOr404(params.id);
      if (c.type === "GROUP") announce(c, { event: "LEFT", actor: person(ME_ID), targets: [], title: null });
      c.memberIds = c.memberIds.filter((id) => id !== ME_ID);
      return undefined;
    },
  },
  {
    /** 메시지 내역 (커서 페이지). 조회만으로는 읽음 처리하지 않습니다. */
    method: "GET",
    path: "/api/v1/conversations/:id/messages",
    handler: ({ params, query }) => messagePage(conversationOr404(params.id).id, query),
  },
  {
    /** 읽음: 본문 { lastReadMessageId }는 선택. 없으면 마지막 메시지까지 */
    method: "PATCH",
    path: "/api/v1/conversations/:id/read",
    handler: ({ params, body }) => {
      markRead(params.id, body?.lastReadMessageId);
      return undefined;
    },
  },
];
