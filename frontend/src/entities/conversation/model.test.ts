import { describe, expect, it } from "vitest";
import { groupTitleError, matchesConversation, messagePreview, systemText, type Conversation, type Message } from "./model";

const message = (over: Partial<Message> = {}): Message => ({
  id: "m",
  conversationId: "c",
  senderId: "u",
  sender: null,
  isMine: false,
  type: "TEXT",
  text: "",
  system: null,
  attachment: null,
  createdAt: "",
  ...over,
});

const base = {
  members: [],
  memberCount: 2,
  unreadCount: 0,
  muted: false,
  createdAt: "",
  updatedAt: "",
};

const direct = (nickname: string, username: string, text: string): Conversation => ({
  ...base,
  id: username,
  type: "DIRECT",
  title: null,
  partner: { id: username, username, nickname, profileImageUrl: null },
  lastMessage: message({ text }),
});

const group: Conversation = {
  ...base,
  id: "g",
  type: "GROUP",
  title: "성수 카페 투어",
  partner: null,
  members: ["seoyeon", "jimin", "minsu"].map((u, i) => ({ id: u, username: u, nickname: u, profileImageUrl: null, isMe: i === 0 })),
  memberCount: 3,
  lastMessage: message({ text: "몇 시에 볼까요" }),
};

describe("matchesConversation", () => {
  const jimin = direct("지민", "jimin", "모모도 같이 들러보세요");

  it("검색어가 없으면 모두", () => expect(matchesConversation(jimin, "  ")).toBe(true));
  it("1:1은 아이디 · 이름으로 찾는다", () => {
    expect(matchesConversation(jimin, "지민")).toBe(true);
    expect(matchesConversation(jimin, "JIMIN")).toBe(true);
  });
  it("마지막 메시지 내용으로 찾고, 띄어쓰기는 무시한다", () => expect(matchesConversation(jimin, "같이들러")).toBe(true));
  it("맞지 않으면 false", () => expect(matchesConversation(jimin, "하은")).toBe(false));
  it("그룹은 이름 · 참여자 아이디로 찾는다", () => {
    expect(matchesConversation(group, "카페투어")).toBe(true);
    expect(matchesConversation(group, "minsu")).toBe(true);
    expect(matchesConversation(group, "haeun")).toBe(false);
  });
});

describe("systemText · messagePreview", () => {
  const actor = { id: "1", username: "seoyeon" };
  const targets = [
    { id: "2", username: "jimin" },
    { id: "3", username: "minsu" },
  ];

  it("만들기 · 초대 · 나가기 · 이름 변경 문구", () => {
    expect(systemText({ event: "CREATED", actor, targets, title: "모임" })).toBe("seoyeon님이 jimin, minsu님과 채팅방을 만들었어요");
    expect(systemText({ event: "INVITED", actor, targets: targets.slice(0, 1), title: null })).toBe("seoyeon님이 jimin님을 초대했어요");
    expect(systemText({ event: "LEFT", actor, targets: [], title: null })).toBe("seoyeon님이 나갔어요");
    expect(systemText({ event: "RENAMED", actor, targets: [], title: "새 이름" })).toBe("seoyeon님이 채팅방 이름을 ‘새 이름’(으)로 바꿨어요");
  });

  it("안내 메시지는 목록 미리보기에도 문구로 나온다", () => {
    expect(messagePreview(message({ type: "SYSTEM", system: { event: "LEFT", actor, targets: [], title: null } }))).toBe("seoyeon님이 나갔어요");
    expect(messagePreview(message({ text: "안녕" }))).toBe("안녕");
  });
});

describe("groupTitleError", () => {
  it("1~20자 (앞뒤 공백 제외, 이모지 한 개 = 1자)", () => {
    expect(groupTitleError(" 모임 ")).toBeNull();
    expect(groupTitleError("😀".repeat(20))).toBeNull();
    expect(groupTitleError("  ")).toBe("채팅방 이름을 입력해 주세요.");
    expect(groupTitleError("가".repeat(21))).toBe("채팅방 이름은 20자까지 쓸 수 있어요.");
  });
});
