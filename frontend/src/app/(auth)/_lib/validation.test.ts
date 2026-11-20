import { describe, expect, it } from "vitest";
import { emailError } from "./validation";

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
