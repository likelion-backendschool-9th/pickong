import type { Message } from "./model";

/**
 * 대화방 메시지 목록을 합치는 순수 함수들. 실시간 이벤트와 HTTP로 다시 불러온 목록이 겹칠 수 있어서
 * 항상 id로 중복을 없애고 서버 id 순서(= 서버가 정한 메시지 순서)로 정렬합니다 (common_blueprint 5.9).
 * 보내는 중 · 실패한 메시지(서버 id 없음)는 맨 뒤에 만든 순서대로 둡니다.
 */

export const pendingId = (clientMessageId: string) => `pending:${clientMessageId}`;

const isPending = (m: Message) => m.id.startsWith("pending:");

const serverOrder = (id: string): bigint | null => (/^\d+$/.test(id) ? BigInt(id) : null);

export function compareMessages(a: Message, b: Message): number {
  const pa = isPending(a);
  const pb = isPending(b);
  if (pa !== pb) return pa ? 1 : -1;
  if (pa && pb) return a.createdAt.localeCompare(b.createdAt);
  const oa = serverOrder(a.id);
  const ob = serverOrder(b.id);
  if (oa !== null && ob !== null) return oa < ob ? -1 : oa > ob ? 1 : 0;
  return a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id);
}

/**
 * 메시지 하나를 넣거나 바꿉니다. 같은 id가 있으면 바꾸고, 서버 확인이 보내는 중인 말풍선과 같은 clientMessageId면
 * 그 말풍선을 서버 메시지로 바꿉니다.
 */
export function upsertMessage(items: Message[], incoming: Message): Message[] {
  const rest = items.filter(
    (m) => m.id !== incoming.id && !(incoming.clientMessageId && isPending(m) && m.clientMessageId === incoming.clientMessageId),
  );
  return [...rest, incoming].sort(compareMessages);
}

/** 여러 메시지를 합칩니다 (이전 내역 · after 보충). */
export function mergeMessages(items: Message[], more: Message[]): Message[] {
  return more.reduce(upsertMessage, items);
}

/** 보내는 중인 메시지를 실패로 표시합니다. 이미 서버 확인을 받았으면 그대로입니다. */
export function markFailed(items: Message[], clientMessageId: string): Message[] {
  return items.map((m) => (isPending(m) && m.clientMessageId === clientMessageId ? { ...m, status: "failed" as const } : m));
}

/** 다시 보낼 때 실패한 말풍선을 보내는 중으로 되돌립니다. */
export function markSending(items: Message[], clientMessageId: string): Message[] {
  return items.map((m) => (isPending(m) && m.clientMessageId === clientMessageId ? { ...m, status: "sending" as const } : m));
}

/** 서버에 저장된 마지막 메시지 (after 보충 · 읽음 처리 기준). 없으면 null */
export function latestServerMessage(items: Message[]): Message | null {
  for (let i = items.length - 1; i >= 0; i -= 1) {
    if (!isPending(items[i])) return items[i];
  }
  return null;
}
