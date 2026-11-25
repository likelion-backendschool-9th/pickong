import { canView, db, findPlace, findUser, findUserByName, ME_ID, nextId, placeDto } from "../db.ts";
import type { ConversationRow, MessageRow } from "../fixtures/types.ts";
import { HttpError, notFound, type Route } from "../router.ts";

/** 내가 속한 대화방만 */
const conversationOr404 = (id: string) => {
  const c = db.conversations.find((x) => x.id === id && x.memberIds.includes(ME_ID));
  return c ?? notFound("대화");
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

function messageDto(m: MessageRow) {
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

/** 보낼 때 첨부: 사진 { type: "IMAGE", url } 또는 장소 { type: "PLACE", placeId } */
function parseAttachment(raw: unknown): MessageRow["attachment"] {
  if (!raw || typeof raw !== "object") return null;
  const a = raw as { type?: string; url?: unknown; placeId?: unknown };
  if (a.type === "IMAGE" && typeof a.url === "string" && a.url) return { type: "IMAGE", url: a.url };
  if (a.type === "PLACE" && typeof a.placeId === "string") {
    if (!findPlace(a.placeId)) throw new HttpError(400, "BAD_PLACE", "첨부한 장소를 찾을 수 없어요.");
    return { type: "PLACE", refId: a.placeId };
  }
  throw new HttpError(400, "BAD_ATTACHMENT", "첨부 형식을 확인해 주세요.");
}

const messagesOf = (conversationId: string) =>
  db.messages.filter((m) => m.conversationId === conversationId).sort((a, b) => a.createdAt.localeCompare(b.createdAt));

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

export const messageRoutes: Route[] = [
  {
    /** 내 대화 목록 (최근 메시지 순). 메시지가 없는 대화방은 빼고 보여 줍니다. */
    method: "GET",
    path: "/api/v1/conversations",
    handler: () =>
      db.conversations
        .filter((c) => c.memberIds.includes(ME_ID) && messagesOf(c.id).length > 0)
        .map(conversationDto)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
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
    /** 대화 시작: 상대와의 대화방이 있으면 그대로, 없으면 새로 만듭니다. */
    method: "POST",
    path: "/api/v1/conversations",
    handler: ({ body }) => {
      const partner = findUserByName(String(body?.username ?? "")) ?? notFound("사용자");
      if (partner.id === ME_ID) throw new HttpError(400, "SELF_DM", "나에게는 메시지를 보낼 수 없어요.");
      let c = db.conversations.find((x) => x.memberIds.includes(ME_ID) && x.memberIds.includes(partner.id));
      if (!c) {
        c = { id: nextId("dm"), memberIds: [ME_ID, partner.id], lastReadAt: {} };
        db.conversations.push(c);
      }
      return conversationDto(c);
    },
  },
  { method: "GET", path: "/api/v1/conversations/:id", handler: ({ params }) => conversationDto(conversationOr404(params.id)) },
  {
    /** 메시지 목록 (오래된 순). 불러오면 읽음 처리합니다. */
    method: "GET",
    path: "/api/v1/conversations/:id/messages",
    handler: ({ params }) => {
      const c = conversationOr404(params.id);
      c.lastReadAt[ME_ID] = new Date().toISOString();
      return messagesOf(c.id).map(messageDto);
    },
  },
  {
    method: "POST",
    path: "/api/v1/conversations/:id/messages",
    delay: 250,
    handler: ({ params, body }) => {
      const c = conversationOr404(params.id);
      const text = String(body?.text ?? "").trim();
      const attachment = parseAttachment(body?.attachment);
      if (!text && !attachment) throw new HttpError(400, "EMPTY_MESSAGE", "메시지를 입력해 주세요.");
      if (text.length > 1000) throw new HttpError(400, "TOO_LONG", "메시지는 1000자까지 보낼 수 있어요.");
      const now = new Date().toISOString();
      const m: MessageRow = { id: nextId("m"), conversationId: c.id, senderId: ME_ID, text, attachment, createdAt: now };
      db.messages.push(m);
      c.lastReadAt[ME_ID] = now;
      return messageDto(m);
    },
  },
];
