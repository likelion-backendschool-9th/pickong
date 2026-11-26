import { describe, expect, it } from "vitest";
import { normalizeReason, reasonError } from "./reason";

describe("관리자 처리 사유", () => {
  it("보충 평면 문자도 코드 포인트 하나로 세어 200자까지 허용한다", () => {
    expect(reasonError("😀".repeat(200), true)).toBeNull();
    expect(reasonError("😀".repeat(201), true)).not.toBeNull();
    expect(reasonError("가".repeat(201), false)).not.toBeNull();
  });

  it("서버가 공백으로 보는 문자만 양끝에서 제거한다", () => {
    const spaces = "\t\n\r\u001c\u001f\u0085\u00a0\u1680\u2003\u2028\u202f\u205f\u3000 ";
    expect(normalizeReason(`${spaces}확인${spaces}`)).toBe("확인");
    expect(reasonError(spaces, true)).not.toBeNull();
    expect(reasonError(spaces, false)).toBeNull();
    expect(normalizeReason("\ufeff확인\ufeff")).toBe("\ufeff확인\ufeff");
  });
});
