import { describe, expect, it } from "vitest";
import { nicknameError, nicknameFeedback, nicknameLockMessage, nicknameStatus, normalizeNicknameInput } from "./nickname";

describe("nicknameError (닉네임 = 사용자 ID)", () => {
  it("3~20자만 허용한다", () => {
    expect(nicknameError("")).toBe("닉네임을 입력해 주세요.");
    expect(nicknameError("ab")).not.toBeNull();
    expect(nicknameError("abc")).toBeNull();
    expect(nicknameError("a".repeat(20))).toBeNull();
    expect(nicknameError("a".repeat(21))).not.toBeNull();
  });

  it("영문만 쓸 수 있고 한글은 길이보다 먼저 막는다", () => {
    expect(nicknameError("seoyeon")).toBeNull();
    expect(nicknameError("seo_yeon.92")).toBeNull();
    expect(nicknameError("서연")).toContain("영문으로만");
    expect(nicknameError("서연abc")).not.toBeNull();
    expect(nicknameError("seo-yeon")).not.toBeNull();
  });

  it("영문으로 시작해야 한다", () => {
    expect(nicknameError("92seoyeon")).not.toBeNull();
    expect(nicknameError("_seoyeon")).not.toBeNull();
    expect(nicknameError(".seoyeon")).not.toBeNull();
  });

  it("마침표는 끝 · 연속으로 쓸 수 없다", () => {
    expect(nicknameError("seoyeon.")).not.toBeNull();
    expect(nicknameError("seo..yeon")).not.toBeNull();
  });

  it("입력값은 소문자로 바꾸고 공백을 지운다", () => {
    expect(normalizeNicknameInput(" Seo Yeon ")).toBe("seoyeon");
  });
});

describe("nicknameStatus", () => {
  it("비어 있으면 아무것도 표시하지 않는다", () => {
    expect(nicknameStatus("", "", undefined)).toBe("idle");
  });

  it("입력이 멈추기 전이나 서버 응답 전에는 확인 중이다", () => {
    expect(nicknameStatus("seoyeon", "seoye", true)).toBe("checking");
    expect(nicknameStatus("seoyeon", "seoyeon", undefined)).toBe("checking");
  });

  it("형식이 틀리면 서버에 묻지 않고 invalid", () => {
    expect(nicknameStatus("서연", "서연", undefined)).toBe("invalid");
  });

  it("서버 응답에 따라 사용 가능 · 사용 중 · 실패로 나눈다", () => {
    expect(nicknameStatus("seoyeon", "seoyeon", true)).toBe("available");
    expect(nicknameStatus("seoyeon", "seoyeon", false)).toBe("taken");
    expect(nicknameStatus("seoyeon", "seoyeon", null)).toBe("error");
  });

  it("프로필 편집에서 지금 쓰는 닉네임은 서버에 묻지 않고 current", () => {
    expect(nicknameStatus("seoyeon", "seoye", undefined, "seoyeon")).toBe("current");
    expect(nicknameStatus("seoyeon2", "seoyeon2", true, "seoyeon")).toBe("available");
  });
});

describe("nicknameFeedback", () => {
  it("오류 상태에는 오류 문구, 나머지는 안내 문구를 준다", () => {
    expect(nicknameFeedback("seoyeon", "taken").error).toBe("이미 사용 중인 닉네임이에요.");
    expect(nicknameFeedback("서연", "invalid").error).toContain("영문으로만");
    expect(nicknameFeedback("seoyeon", "available")).toEqual({ error: null, hint: "사용할 수 있는 닉네임이에요." });
    expect(nicknameFeedback("seoyeon", "current").error).toBeNull();
  });
});

describe("nicknameLockMessage", () => {
  const now = new Date(2026, 10, 24, 12, 0).getTime();

  it("다음 변경 가능 시각이 아직 오지 않았으면 그 날짜를 안내한다", () => {
    expect(nicknameLockMessage(new Date(2026, 11, 8, 9, 30).toISOString(), now)).toBe("12월 8일부터 다시 바꿀 수 있어요.");
  });

  it("지금 바꿀 수 있거나(null) 그 시각이 지났으면 잠그지 않는다", () => {
    expect(nicknameLockMessage(null, now)).toBeNull();
    expect(nicknameLockMessage(new Date(2026, 10, 24, 11, 59).toISOString(), now)).toBeNull();
  });

  it("읽을 수 없는 값이면 잠그지 않는다 (서버가 최종 판단)", () => {
    expect(nicknameLockMessage("not-a-date", now)).toBeNull();
  });
});
