import type { PlaceCategory } from "@/entities/place";
import { http } from "@/shared/api/http";
import type { PlaceSubmissionDto } from "./dto";
import { toPlaceSubmission } from "./mapper";

export interface SubmitPlaceInput {
  name: string;
  lat: number;
  lng: number;
  description: string;
  category: PlaceCategory;
  imageUrl: string | null;
}

/** 장소 제보. 화면은 이 함수만 호출하고, mock과 실제 API는 같은 경로를 씁니다. */
export const placeSubmissionApi = {
  /** 사용자 제보. 승인 전에는 공개 장소 목록에 들어가지 않습니다. */
  submit: async (input: SubmitPlaceInput) => toPlaceSubmission(await http.post<PlaceSubmissionDto>("/place-submissions", input)),
  list: async () => (await http.get<PlaceSubmissionDto[]>("/admin/place-submissions")).map(toPlaceSubmission),
  detail: async (id: string) => toPlaceSubmission(await http.get<PlaceSubmissionDto>(`/admin/place-submissions/${id}`)),
  approve: async (id: string) => toPlaceSubmission(await http.post<PlaceSubmissionDto>(`/admin/place-submissions/${id}/approve`)),
  reject: async (id: string, reason: string) =>
    toPlaceSubmission(await http.post<PlaceSubmissionDto>(`/admin/place-submissions/${id}/reject`, { reason })),
};
