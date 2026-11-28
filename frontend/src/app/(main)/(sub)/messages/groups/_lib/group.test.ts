import { describe, expect, it } from "vitest";
import { normalizeUsername, pickError, remainingSeats } from "./group";

describe("normalizeUsername", () => {
  it("앞뒤 공백 · 맨 앞 @를 지우고 소문자로", () => {
    expect(normalizeUsername("  @JiMin ")).toBe("jimin");
    expect(normalizeUsername("minsu")).toBe("minsu");
  });
});

describe("pickError", () => {
  it("빈 아이디 · 이미 고른 사람 · 자리 없음", () => {
    expect(pickError("", [], 5)).toBe("아이디를 입력해 주세요.");
    expect(pickError("jimin", ["jimin"], 5)).toBe("이미 추가한 사람이에요.");
    expect(pickError("haeun", [], 0)).toBe("그룹 채팅은 10명까지 참여할 수 있어요.");
    expect(pickError("haeun", ["jimin"], 1)).toBeNull();
  });
});

describe("remainingSeats", () => {
  it("나 포함 10명까지", () => {
    expect(remainingSeats(1)).toBe(9);
    expect(remainingSeats(10)).toBe(0);
    expect(remainingSeats(12)).toBe(0);
  });
});
