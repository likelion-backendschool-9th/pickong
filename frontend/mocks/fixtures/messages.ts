import type { ConversationRow, MessageRow } from "./types.ts";

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

/** 예시 DM. 서연(내 계정)과 지민 · 하은 · 민수의 대화 */
export const conversations: ConversationRow[] = [
  { id: "dm-jimin", memberIds: ["u-seoyeon", "u-jimin"], lastReadAt: { "u-seoyeon": ago(100), "u-jimin": ago(1) } },
  { id: "dm-minsu", memberIds: ["u-seoyeon", "u-minsu"], lastReadAt: { "u-seoyeon": ago(60 * 10), "u-minsu": ago(60 * 5) } },
  { id: "dm-haeun", memberIds: ["u-seoyeon", "u-haeun"], lastReadAt: { "u-seoyeon": ago(1), "u-haeun": ago(1) } },
];

export const messages: MessageRow[] = [
  { id: "m-1", conversationId: "dm-jimin", senderId: "u-jimin", text: "서연님 지난번에 말한 카페 여기예요!", attachment: { type: "POST", refId: "post-1" }, createdAt: ago(180) },
  { id: "m-2", conversationId: "dm-jimin", senderId: "u-seoyeon", text: "오 창가 자리 너무 좋다. 이번 주말에 가볼게요", attachment: null, createdAt: ago(150) },
  { id: "m-3", conversationId: "dm-jimin", senderId: "u-jimin", text: "모모도 같이 들러보세요. 컵이 예뻐요", attachment: { type: "PLACE", refId: "p-momo" }, createdAt: ago(40) },
  { id: "m-4", conversationId: "dm-jimin", senderId: "u-jimin", text: "11시쯤이 햇빛 제일 좋아요 ☀️", attachment: null, createdAt: ago(38) },
  { id: "m-5", conversationId: "dm-minsu", senderId: "u-minsu", text: "베이커리 소금 소금빵 진짜 맛있어요", attachment: null, createdAt: ago(60 * 5) },
  { id: "m-6", conversationId: "dm-haeun", senderId: "u-haeun", text: "책방 오후 다음에 같이 가요", attachment: { type: "PLACE", refId: "p-book" }, createdAt: ago(60 * 26) },
  { id: "m-7", conversationId: "dm-haeun", senderId: "u-seoyeon", text: "좋아요! 토요일 어때요?", attachment: null, createdAt: ago(60 * 25) },
];
