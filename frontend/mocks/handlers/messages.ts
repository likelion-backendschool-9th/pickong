import { canView, db, findPlace, findUser, findUserByName, ME_ID, nextId, placeDto } from "../db.ts";
import type { ConversationRow, MessageRow } from "../fixtures/types.ts";
import { HttpError, notFound, RawResponse, type Route } from "../router.ts";

/*
 * 1:1 DM. 백엔드와 같은 계약입니다 (backend_blueprint 3장 conversation, common_blueprint 5.9).
 * - HTTP: 목록 · 안 읽은 수 · 열기(/direct) · 상세 · 메시지 커서 페이지 · 읽음. 메시지 보내기 HTTP API는 없습니다.
 * - 보내기 · 실시간은 STOMP(mocks/realtime.ts)가 이 파일의 sendDirectMessage · markRead를 부릅니다.
 * - 메시지 id는 숫자 문자열이라 id 순서가 곧 메시지 순서입니다.
 */

/** 내가 속한 대화방만 */
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
  return {
    id: m.id,
    conversationId: m.conversationId,
    senderId: m.senderId,
    isMine: m.senderId === ME_ID,
    text: m.text,
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
  return {
    id: c.id,
    partner: userSummary(c.memberIds.find((id) => id !== ME_ID)!),
    lastMessage: last ? messageDto(last) : null,
    unreadCount: list.filter((m) => m.senderId !== ME_ID && m.createdAt > readAt).length,
    updatedAt: last?.createdAt ?? readAt,
  };
}

const MAX_TEXT = 2_000;
let messageSeq = 1_000;
/** 같은 clientMessageId로 다시 보내면 저장하지 않습니다 (백엔드 UNIQUE(sender_id, client_message_id)) */
const sentByClientId = new Map<string, string>();

/**
 * 메시지 보내기 (STOMP /app/dm.send). 백엔드와 같은 규칙 · 오류 코드입니다.
 * @returns 저장한(또는 이미 있던) 메시지와, 이번에 새로 저장했는지
 */
export function sendDirectMessage(body: { conversationId?: unknown; clientMessageId?: unknown; text?: unknown; attachment?: unknown }) {
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
  const target =
    lastReadMessageId == null
      ? list.at(-1)
      : (list.find((m) => m.id === String(lastReadMessageId)) ??
        notFoundMessage());
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

export const messageRoutes: Route[] = [
  {
    /** 내 대화 목록 (최근 메시지 순). 메시지가 없는 대화방은 빼고 보여 줍니다. */
    method: "GET",
    path: "/api/v1/conversations",
    handler: () =>
      db.conversations
        .filter((c) => c.memberIds.includes(ME_ID) && messagesOf(c.id).length > 0)
        .map(conversationDto)
        .sort((a, b) => Number(b.lastMessage!.id) - Number(a.lastMessage!.id)),
  },
  {
    /** 안 읽은 메시지 수 (하단바 배지) */
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
      let c = db.conversations.find((x) => x.memberIds.includes(ME_ID) && x.memberIds.includes(partner.id));
      const created = !c;
      if (!c) {
        c = { id: nextId("dm"), memberIds: [ME_ID, partner.id], lastReadAt: {} };
        db.conversations.push(c);
      }
      const dto = conversationDto(c);
      return created
        ? new RawResponse(201, { "Content-Type": "application/json; charset=utf-8" }, JSON.stringify(dto))
        : dto;
    },
  },
  { method: "GET", path: "/api/v1/conversations/:id", handler: ({ params }) => conversationDto(conversationOr404(params.id)) },
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
