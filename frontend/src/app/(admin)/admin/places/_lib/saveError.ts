import { ApiError, errorMessage } from "@/shared/api/errors";

export type AdminPlaceWriteStage = "upload" | "save";

/**
 * 사진 업로드가 실패하면 장소 저장을 시도하지 않았다는 안내를 씁니다.
 * 장소 저장 자체의 오류는 등록·수정 문구를 유지합니다.
 */
export function adminPlaceWriteErrorMessage(mode: "create" | "update", stage: AdminPlaceWriteStage, cause: unknown) {
  if (stage === "upload") {
    return mode === "create" ? "사진을 올리지 못했어요. 장소는 등록하지 않았어요." : "사진을 올리지 못했어요. 장소는 수정하지 않았어요.";
  }
  if (cause instanceof ApiError && cause.status === 0) {
    return mode === "create"
      ? "등록 결과를 확인하지 못했어요. 목록에서 장소가 생겼는지 확인한 뒤 다시 시도해 주세요."
      : "수정 결과를 확인하지 못했어요. 상세 내용을 다시 확인해 주세요.";
  }
  if (mode === "update" && cause instanceof ApiError && cause.status === 404) return "이미 삭제되었거나 찾을 수 없는 장소예요.";
  if (mode === "update" && cause instanceof ApiError && cause.code === "PLACE_NOT_MANAGEABLE") return "현재 수정할 수 없는 장소예요.";
  if (mode === "update" && cause instanceof ApiError && cause.status >= 500) return "장소를 수정하지 못했어요. 다시 시도해 주세요.";
  return errorMessage(cause);
}
