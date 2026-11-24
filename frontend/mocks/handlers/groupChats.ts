import { findUser, ME_ID } from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";

/* 그룹 채팅방 관리: 만들기 · 이름 변경 · 초대 · 내보내기 · 알림 끄기 · 나가기 */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();
const MAX_MEMBERS = 30;

interface GroupRow {
  id: string;
  name: string;
  ownerId: string;
  memberIds: string[];
  mutedBy: string[];
  createdAt: string;
  updatedAt: string;
}

let groups: GroupRow[] = [
  { id: "g-1", name: "성수 카페 투어", ownerId: ME_ID, memberIds: [ME_ID, "u-jimin", "u-minsu", "u-yujin"], mutedBy: [], createdAt: ago(20), updatedAt: ago(0.1) },
  { id: "g-2", name: "주말 사진 모임", ownerId: "u-haeun", memberIds: ["u-haeun", ME_ID, "u-walker"], mutedBy: [ME_ID], createdAt: ago(45), updatedAt: ago(2) },
];

const userSummary = (id: string) => {
  const u = findUser(id)!;
  return { id: u.id, username: u.username, nickname: u.nickname, profileImageUrl: u.profileImageUrl };
};

const groupOr404 = (id: string) => groups.find((g) => g.id === id && g.memberIds.includes(ME_ID)) ?? notFound("그룹 채팅");
const ownerOnly = (g: GroupRow) => {
  if (g.ownerId !== ME_ID) throw new HttpError(403, "NOT_OWNER", "방장만 할 수 있어요.");
};

const groupDto = (g: GroupRow) => ({
  id: g.id,
  name: g.name,
  owner: userSummary(g.ownerId),
  members: g.memberIds.map((id) => ({ ...userSummary(id), isOwner: id === g.ownerId, isMe: id === ME_ID })),
  isOwner: g.ownerId === ME_ID,
  muted: g.mutedBy.includes(ME_ID),
  createdAt: g.createdAt,
  updatedAt: g.updatedAt,
});

const validName = (raw: unknown) => {
  const name = String(raw ?? "").trim();
  if (!name) throw new HttpError(400, "INVALID_INPUT", "채팅방 이름을 입력해 주세요.");
  if (name.length > 20) throw new HttpError(400, "INVALID_INPUT", "채팅방 이름은 20자까지 쓸 수 있어요.");
  return name;
};

const validUserIds = (raw: unknown) => {
  if (!Array.isArray(raw) || raw.length === 0) throw new HttpError(400, "INVALID_INPUT", "초대할 사람을 골라 주세요.");
  if (raw.some((id) => !findUser(String(id)))) throw new HttpError(400, "INVALID_INPUT", "초대할 수 없는 사용자가 있어요.");
  return raw.map(String);
};

export const groupChatRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/group-chats",
    handler: () => groups.filter((g) => g.memberIds.includes(ME_ID)).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).map(groupDto),
  },
  {
    method: "POST",
    path: "/api/v1/group-chats",
    handler: ({ body }) => {
      const name = validName(body?.name);
      const ids = validUserIds(body?.memberIds).filter((id) => id !== ME_ID);
      if (ids.length < 2) throw new HttpError(400, "INVALID_INPUT", "그룹 채팅은 나를 빼고 2명 이상 초대해 주세요.");
      if (ids.length + 1 > MAX_MEMBERS) throw new HttpError(400, "INVALID_INPUT", `최대 ${MAX_MEMBERS}명까지 참여할 수 있어요.`);
      const now = new Date().toISOString();
      const g: GroupRow = { id: `g-${Date.now()}`, name, ownerId: ME_ID, memberIds: [ME_ID, ...new Set(ids)], mutedBy: [], createdAt: now, updatedAt: now };
      groups.push(g);
      return groupDto(g);
    },
  },
  { method: "GET", path: "/api/v1/group-chats/:id", handler: ({ params }) => groupDto(groupOr404(params.id)) },
  {
    /** 이름 변경은 방장만, 알림 끄기는 각자 */
    method: "PATCH",
    path: "/api/v1/group-chats/:id",
    handler: ({ params, body }) => {
      const g = groupOr404(params.id);
      if (body?.name !== undefined) {
        ownerOnly(g);
        g.name = validName(body.name);
      }
      if (typeof body?.muted === "boolean") {
        g.mutedBy = body.muted ? [...new Set([...g.mutedBy, ME_ID])] : g.mutedBy.filter((id) => id !== ME_ID);
      }
      return groupDto(g);
    },
  },
  {
    method: "POST",
    path: "/api/v1/group-chats/:id/members",
    handler: ({ params, body }) => {
      const g = groupOr404(params.id);
      const ids = validUserIds(body?.userIds).filter((id) => !g.memberIds.includes(id));
      if (g.memberIds.length + ids.length > MAX_MEMBERS) throw new HttpError(400, "INVALID_INPUT", `최대 ${MAX_MEMBERS}명까지 참여할 수 있어요.`);
      g.memberIds.push(...ids);
      return groupDto(g);
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/group-chats/:id/members/:userId",
    handler: ({ params }) => {
      const g = groupOr404(params.id);
      ownerOnly(g);
      if (params.userId === ME_ID) throw new HttpError(400, "INVALID_INPUT", "방장은 나가기로 채팅방을 떠날 수 있어요.");
      g.memberIds = g.memberIds.filter((id) => id !== params.userId);
      return groupDto(g);
    },
  },
  {
    /** 나가기: 방장이 나가면 가장 먼저 들어온 멤버가 방장이 되고, 아무도 없으면 방이 사라집니다. */
    method: "POST",
    path: "/api/v1/group-chats/:id/leave",
    handler: ({ params }) => {
      const g = groupOr404(params.id);
      g.memberIds = g.memberIds.filter((id) => id !== ME_ID);
      g.mutedBy = g.mutedBy.filter((id) => id !== ME_ID);
      if (g.memberIds.length === 0) groups = groups.filter((x) => x.id !== g.id);
      else if (g.ownerId === ME_ID) g.ownerId = g.memberIds[0];
      return undefined;
    },
  },
];
