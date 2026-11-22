import { describe, expect, it } from "vitest";
import { emailError, passwordConfirmError, passwordError } from "./validation";

describe("emailError", () => {
  it("비어 있으면 입력을 요청한다", () => {
    expect(emailError("  ")).toBe("이메일을 입력해 주세요.");
  });

  it("형식이 틀리면 오류를 돌려준다", () => {
    expect(emailError("pickong")).toBe("이메일 형식을 확인해 주세요.");
    expect(emailError("pickong@mail")).toBe("이메일 형식을 확인해 주세요.");
  });

  it("앞뒤 공백을 무시하고 올바른 이메일을 통과시킨다", () => {
    expect(emailError(" hello@pickong.kr ")).toBeNull();
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

describe("passwordConfirmError", () => {
  it("비어 있거나 다르면 오류, 같으면 통과", () => {
    expect(passwordConfirmError("pickong1", "")).toBe("비밀번호를 한 번 더 입력해 주세요.");
    expect(passwordConfirmError("pickong1", "pickong2")).toBe("비밀번호가 서로 달라요.");
    expect(passwordConfirmError("pickong1", "pickong1")).toBeNull();
  });
});
