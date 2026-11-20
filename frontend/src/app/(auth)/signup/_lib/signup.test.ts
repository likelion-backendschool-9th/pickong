import { describe, expect, it } from "vitest";
import {
  NO_AGREEMENTS,
  hasRequiredAgreements,
  isAllAgreed,
  nicknameError,
  passwordError,
  toggleAllAgreements,
} from "./signup";

describe("nicknameError", () => {
  it("2~10자만 허용한다", () => {
    expect(nicknameError("")).toBe("닉네임을 입력해 주세요.");
    expect(nicknameError("콩")).not.toBeNull();
    expect(nicknameError("찜콩")).toBeNull();
    expect(nicknameError("가나다라마바사아자차")).toBeNull();
    expect(nicknameError("가나다라마바사아자차카")).not.toBeNull();
  });

  it("앞뒤 공백은 글자 수에서 뺀다", () => {
    expect(nicknameError("  콩  ")).not.toBeNull();
  });
});

describe("passwordError", () => {
  it("영문과 숫자를 모두 포함한 8자 이상이어야 한다", () => {
    expect(passwordError("")).toBe("비밀번호를 입력해 주세요.");
    expect(passwordError("abc123")).not.toBeNull();
    expect(passwordError("abcdefgh")).not.toBeNull();
    expect(passwordError("12345678")).not.toBeNull();
    expect(passwordError("pickong1")).toBeNull();
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
