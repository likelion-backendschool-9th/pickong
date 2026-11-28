import type { ConversationRow, MessageRow } from "./types.ts";

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

/** 예시 대화: 서연(내 계정)과 지민 · 하은 · 민수의 1:1, 그리고 그룹 두 개. 메시지 id는 백엔드처럼 숫자 문자열(= 순서) */
export const conversations: ConversationRow[] = [
  { id: "dm-jimin", type: "DIRECT", memberIds: ["u-seoyeon", "u-jimin"], lastReadAt: { "u-seoyeon": ago(100), "u-jimin": ago(1) }, createdAt: ago(60 * 24) },
  { id: "dm-minsu", type: "DIRECT", memberIds: ["u-seoyeon", "u-minsu"], lastReadAt: { "u-seoyeon": ago(60 * 10), "u-minsu": ago(60 * 5) }, createdAt: ago(60 * 24) },
  { id: "dm-haeun", type: "DIRECT", memberIds: ["u-seoyeon", "u-haeun"], lastReadAt: { "u-seoyeon": ago(1), "u-haeun": ago(1) }, createdAt: ago(60 * 48) },
  {
    id: "g-1",
    type: "GROUP",
    title: "성수 카페 투어",
    memberIds: ["u-seoyeon", "u-jimin", "u-minsu", "u-yujin"],
    lastReadAt: { "u-seoyeon": ago(60 * 3) },
    createdAt: ago(60 * 24 * 20),
  },
  {
    id: "g-2",
    type: "GROUP",
    title: "주말 사진 모임",
    memberIds: ["u-haeun", "u-seoyeon", "u-walker"],
    lastReadAt: { "u-seoyeon": ago(1) },
    mutedBy: ["u-seoyeon"],
    createdAt: ago(60 * 24 * 45),
  },
];

/** 안내 메시지의 사람 (아이디는 users 픽스처와 같게) */
const USERNAMES: Record<string, string> = { "u-walker": "seongsu_walker" };
const person = (id: string) => ({ id, username: USERNAMES[id] ?? id.replace(/^u-/, "") });

export const messages: MessageRow[] = [
  { id: "1", conversationId: "dm-jimin", senderId: "u-jimin", text: "서연님 지난번에 말한 카페 여기예요!", attachment: { type: "POST", refId: "post-1" }, createdAt: ago(180) },
  { id: "2", conversationId: "dm-jimin", senderId: "u-seoyeon", text: "오 창가 자리 너무 좋다. 이번 주말에 가볼게요", attachment: null, createdAt: ago(150) },
  { id: "3", conversationId: "dm-jimin", senderId: "u-jimin", text: "모모도 같이 들러보세요. 컵이 예뻐요", attachment: { type: "PLACE", refId: "p-momo" }, createdAt: ago(40) },
  { id: "4", conversationId: "dm-jimin", senderId: "u-jimin", text: "11시쯤이 햇빛 제일 좋아요 ☀️", attachment: null, createdAt: ago(38) },
  { id: "5", conversationId: "dm-minsu", senderId: "u-minsu", text: "베이커리 소금 소금빵 진짜 맛있어요", attachment: null, createdAt: ago(60 * 5) },
  { id: "6", conversationId: "dm-haeun", senderId: "u-haeun", text: "책방 오후 다음에 같이 가요", attachment: { type: "PLACE", refId: "p-book" }, createdAt: ago(60 * 26) },
  { id: "7", conversationId: "dm-haeun", senderId: "u-seoyeon", text: "좋아요! 토요일 어때요?", attachment: null, createdAt: ago(60 * 25) },
  {
    id: "8",
    conversationId: "g-2",
    senderId: "u-haeun",
    type: "SYSTEM",
    text: "",
    system: { event: "CREATED", actor: person("u-haeun"), targets: [person("u-seoyeon"), person("u-walker")], title: "주말 사진 모임" },
    attachment: null,
    createdAt: ago(60 * 24 * 45),
  },
  { id: "9", conversationId: "g-2", senderId: "u-walker", text: "이번 주는 서울숲 어때요?", attachment: null, createdAt: ago(60 * 2) },
  {
    id: "10",
    conversationId: "g-1",
    senderId: "u-seoyeon",
    type: "SYSTEM",
    text: "",
    system: { event: "CREATED", actor: person("u-seoyeon"), targets: [person("u-jimin"), person("u-minsu")], title: "성수 카페 투어" },
    attachment: null,
    createdAt: ago(60 * 24 * 20),
  },
  {
    id: "11",
    conversationId: "g-1",
    senderId: "u-jimin",
    type: "SYSTEM",
    text: "",
    system: { event: "INVITED", actor: person("u-jimin"), targets: [person("u-yujin")], title: null },
    attachment: null,
    createdAt: ago(60 * 24 * 19),
  },
  { id: "12", conversationId: "g-1", senderId: "u-minsu", text: "토요일 11시 성수역 3번 출구 어때요", attachment: null, createdAt: ago(60 * 4) },
  { id: "13", conversationId: "g-1", senderId: "u-yujin", text: "좋아요! 모모 먼저 들러요", attachment: null, createdAt: ago(60 * 1) },
];
