import { describe, expect, it } from "vitest";
import { matchesConversation, type Conversation } from "./model";

const conv = (nickname: string, username: string, text: string): Conversation => ({
  id: username,
  partner: { id: username, username, nickname, profileImageUrl: null },
  lastMessage: { id: "m", conversationId: username, senderId: username, isMine: false, text, attachment: null, createdAt: "" },
  unreadCount: 0,
  updatedAt: "",
});

describe("matchesConversation", () => {
  const jimin = conv("지민", "jimin", "모모도 같이 들러보세요");

  it("검색어가 없으면 모두", () => expect(matchesConversation(jimin, "  ")).toBe(true));
  it("아이디 · 이름으로 찾는다", () => {
    expect(matchesConversation(jimin, "지민")).toBe(true);
    expect(matchesConversation(jimin, "JIMIN")).toBe(true);
  });
  it("마지막 메시지 내용으로 찾고, 띄어쓰기는 무시한다", () => expect(matchesConversation(jimin, "같이들러")).toBe(true));
  it("맞지 않으면 false", () => expect(matchesConversation(jimin, "하은")).toBe(false));
});
