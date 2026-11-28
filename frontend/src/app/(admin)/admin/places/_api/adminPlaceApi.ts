import { ApiError } from "@/shared/api/errors";
import { API_BASE, http } from "@/shared/api/http";
import type { AdminPlaceDetailDto, AdminPlaceSummaryDto } from "./dto";
import { toAdminPlaceCreateRequest, toAdminPlaceDetail, toAdminPlaceSummary, toAdminPlaceUpdateRequest } from "./mapper";
import type { AdminPlaceCreateInput, AdminPlaceUpdateInput } from "../_lib/models";

/**
 * 관리자 장소 API. 회원 /places, 제보 /admin/place-submissions와 다른 경로입니다.
 * 백엔드에는 아직 없습니다. 개발 Mock은 mocks/handlers/adminPlaces.ts가 같은 경로로 응답합니다.
 * uploadMockImage는 개발 Mock 전용입니다. 실제 관리자 미디어 API 계약이 아닙니다.
 * http가 공통 오류 { code, message }를 ApiError로 바꿉니다.
 *
 * 제안 상태 코드: 목록·상세·수정 200, 등록 201, 삭제 204.
 * 제안 오류: 400 INVALID_INPUT, 401 UNAUTHORIZED, 403 FORBIDDEN·INVALID_CSRF_TOKEN,
 * 404 PLACE_NOT_FOUND, 409 PLACE_NOT_MANAGEABLE(SYSTEM이 아닌 장소의 수정·삭제).
 */
function placePath(id: string) {
  return `/admin/places/${encodeURIComponent(id)}`;
}

export const adminPlaceApi = {
  list: async () => (await http.get<AdminPlaceSummaryDto[]>("/admin/places")).map(toAdminPlaceSummary),
  detail: async (id: string, signal?: AbortSignal) => toAdminPlaceDetail(await http.get<AdminPlaceDetailDto>(placePath(id), { signal })),
  create: async (input: AdminPlaceCreateInput) =>
    toAdminPlaceDetail(await http.post<AdminPlaceDetailDto>("/admin/places", toAdminPlaceCreateRequest(input))),
  update: async (id: string, input: AdminPlaceUpdateInput) =>
    toAdminPlaceDetail(await http.patch<AdminPlaceDetailDto>(placePath(id), toAdminPlaceUpdateRequest(input))),
  remove: (id: string) => http.delete<void>(placePath(id)),
  /** 개발 Mock에만 사진을 올리고, 장소 저장에 넣을 주소를 받습니다. 백엔드 계약이 아닙니다. */
  uploadMockImage: async (file: Blob & { type: string }) => {
    const response = await fetch(`${API_BASE}/admin/places/mock-media`, {
      method: "POST",
      headers: { "Content-Type": file.type || "application/octet-stream" },
      body: file,
      credentials: "include",
    });
    const data = (await response.json().catch(() => null)) as { imageUrl?: unknown; code?: string; message?: string } | null;
    if (!response.ok) {
      throw new ApiError(response.status, data?.code ?? "UNKNOWN", data?.message ?? "사진을 올리지 못했어요.");
    }
    if (typeof data?.imageUrl !== "string" || !data.imageUrl) {
      throw new ApiError(500, "INTERNAL", "사진 주소를 받지 못했어요.");
    }
    return data.imageUrl;
  },
};
