import { describe, expect, it } from "vitest";
import { ApiError } from "@/shared/api/errors";
import { adminPlaceWriteErrorMessage } from "./saveError";

describe("관리자 장소 저장 오류 문구", () => {
  it("사진 업로드가 실패하면 장소 저장을 하지 않았다고 안내한다", () => {
    const upload = new ApiError(500, "UPLOAD_FAILED", "사진을 올리지 못했어요.");
    expect(adminPlaceWriteErrorMessage("update", "upload", upload)).toBe("사진을 올리지 못했어요. 장소는 수정하지 않았어요.");
    expect(adminPlaceWriteErrorMessage("create", "upload", new TypeError("network"))).toBe("사진을 올리지 못했어요. 장소는 등록하지 않았어요.");
  });

  it("장소 저장이 실패하면 업로드 안내와 다른 문구를 쓴다", () => {
    expect(adminPlaceWriteErrorMessage("update", "save", new ApiError(500, "INTERNAL", "잠시 오류"))).toBe("장소를 수정하지 못했어요. 다시 시도해 주세요.");
    expect(adminPlaceWriteErrorMessage("update", "save", new ApiError(404, "PLACE_NOT_FOUND", "장소를 찾을 수 없어요."))).toBe("이미 삭제되었거나 찾을 수 없는 장소예요.");
    expect(adminPlaceWriteErrorMessage("create", "save", new ApiError(400, "INVALID_INPUT", "이름을 입력해 주세요."))).toBe("이름을 입력해 주세요.");
  });
});
