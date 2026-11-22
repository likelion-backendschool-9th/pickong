import { describe, expect, it } from "vitest";
import {
  NO_AGREEMENTS,
  hasRequiredAgreements,
  isAllAgreed,
  nicknameError,
  nicknameStatus,
  previousStep,
  stepNumber,
  toggleAllAgreements,
} from "./signup";

describe("가입 단계", () => {
  it("인증 → 비밀번호 → 닉네임 → 약관 순서로 번호를 매긴다", () => {
    expect(stepNumber("verify")).toBe(1);
    expect(stepNumber("password")).toBe(2);
    expect(stepNumber("nickname")).toBe(3);
    expect(stepNumber("terms")).toBe(4);
  });

  it("이전 단계로 돌아가고, 첫 단계에서는 null을 돌려준다", () => {
    expect(previousStep("terms")).toBe("nickname");
    expect(previousStep("password")).toBe("verify");
    expect(previousStep("verify")).toBeNull();
  });
});

describe("nicknameError", () => {
  it("2~10자만 허용한다", () => {
    expect(nicknameError("")).toBe("닉네임을 입력해 주세요.");
    expect(nicknameError("콩")).not.toBeNull();
    expect(nicknameError("찜콩")).toBeNull();
    expect(nicknameError("가나다라마바사아자차")).toBeNull();
    expect(nicknameError("가나다라마바사아자차카")).not.toBeNull();
  });

  it("로그인 아이디와 헷갈리지 않도록 @를 막는다", () => {
    expect(nicknameError("찜@콩")).not.toBeNull();
  });

  it("앞뒤 공백은 글자 수에서 뺀다", () => {
    expect(nicknameError("  콩  ")).not.toBeNull();
  });
});

describe("nicknameStatus", () => {
  it("비어 있으면 아무것도 표시하지 않는다", () => {
    expect(nicknameStatus("  ", "  ", undefined)).toBe("idle");
  });

  it("입력이 멈추기 전이나 서버 응답 전에는 확인 중이다", () => {
    expect(nicknameStatus("찜콩이", "찜콩", true)).toBe("checking");
    expect(nicknameStatus("찜콩", "찜콩", undefined)).toBe("checking");
  });

  it("형식이 틀리면 서버에 묻지 않고 invalid", () => {
    expect(nicknameStatus("콩", "콩", undefined)).toBe("invalid");
  });

  it("서버 응답에 따라 사용 가능 · 사용 중 · 실패로 나눈다", () => {
    expect(nicknameStatus("찜콩", "찜콩", true)).toBe("available");
    expect(nicknameStatus("찜콩", "찜콩", false)).toBe("taken");
    expect(nicknameStatus("찜콩", "찜콩", null)).toBe("error");
  });
});

describe("약관 동의", () => {
  it("필수 항목만 동의해도 진행할 수 있다", () => {
    const agreements = { terms: true, privacy: true, marketing: false };
    expect(hasRequiredAgreements(agreements)).toBe(true);
    expect(isAllAgreed(agreements)).toBe(false);
  });

  it("필수 항목이 빠지면 진행할 수 없다", () => {
    expect(hasRequiredAgreements({ terms: true, privacy: false, marketing: true })).toBe(false);
  });

  it("모두 동의는 일부만 켜져 있으면 전부 켜고, 전부 켜져 있으면 전부 끈다", () => {
    const all = toggleAllAgreements({ terms: true, privacy: false, marketing: false });
    expect(all).toEqual({ terms: true, privacy: true, marketing: true });
    expect(toggleAllAgreements(all)).toEqual(NO_AGREEMENTS);
  });
});
