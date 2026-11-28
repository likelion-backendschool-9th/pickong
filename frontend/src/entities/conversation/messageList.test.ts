import { describe, expect, it } from "vitest";
import type { Message } from "./model";
import { latestServerMessage, markFailed, markSending, mergeMessages, pendingId, upsertMessage } from "./messageList";

const msg = (id: string, over: Partial<Message> = {}): Message => ({
  id,
  conversationId: "1",
  senderId: "u",
  sender: null,
  isMine: false,
  type: "TEXT",
  text: id,
  system: null,
  attachment: null,
  createdAt: "2026-11-28T03:00:00Z",
  ...over,
});

const pending = (cmid: string, createdAt = "2026-11-28T03:00:00Z") =>
  msg(pendingId(cmid), { clientMessageId: cmid, isMine: true, status: "sending", createdAt });

describe("upsertMessage · mergeMessages", () => {
  it("서버 id 순서로 정렬하고 같은 id는 하나만 (자릿수가 달라도 숫자로 비교)", () => {
    const items = mergeMessages([msg("9"), msg("10")], [msg("2"), msg("10", { text: "바뀐 10" })]);
    expect(items.map((m) => m.id)).toEqual(["2", "9", "10"]);
    expect(items[2].text).toBe("바뀐 10");
  });

  it("서버 확인이 오면 같은 clientMessageId의 보내는 중 말풍선을 바꾼다", () => {
    const items = upsertMessage([msg("1"), pending("c1")], msg("2", { clientMessageId: "c1", isMine: true }));
    expect(items.map((m) => m.id)).toEqual(["1", "2"]);
    expect(items[1].status).toBeUndefined();
  });

  it("보내는 중인 메시지는 맨 뒤, 만든 순서대로", () => {
    const items = mergeMessages([], [pending("b", "2026-11-28T03:00:02Z"), msg("5"), pending("a", "2026-11-28T03:00:01Z")]);
    expect(items.map((m) => m.id)).toEqual(["5", pendingId("a"), pendingId("b")]);
  });

  it("같은 확인이 두 번 와도(바로 보낸 것 + 중계) 하나만", () => {
    const confirmed = msg("7", { clientMessageId: "c", isMine: true });
    expect(mergeMessages([pending("c")], [confirmed, confirmed]).map((m) => m.id)).toEqual(["7"]);
  });
});

describe("markFailed · markSending · latestServerMessage", () => {
  it("보내는 중 → 실패 → 다시 보내는 중", () => {
    const failed = markFailed([pending("c")], "c");
    expect(failed[0].status).toBe("failed");
    expect(markSending(failed, "c")[0].status).toBe("sending");
  });

  it("마지막 서버 메시지는 보내는 중인 메시지를 건너뛴다", () => {
    expect(latestServerMessage([msg("3"), pending("c")])?.id).toBe("3");
    expect(latestServerMessage([pending("c")])).toBeNull();
  });
});
