import type { LatLng } from "@/shared/lib/geo";
import type { PlaceSubmission } from "../model";
import type { PlaceSubmissionDto } from "./dto";

const UNKNOWN_SUBMITTER = { id: "", username: "unknown", nickname: "알 수 없는 사용자" };

/** 지도에 올릴 수 있는 좌표만 남깁니다. 범위 밖이거나 숫자가 아니면 null입니다. */
export function toLocation(lat: number | null, lng: number | null): LatLng | null {
  if (lat == null || lng == null) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  return { lat, lng };
}

export function toPlaceSubmission(dto: PlaceSubmissionDto): PlaceSubmission {
  return {
    id: dto.id,
    name: dto.name,
    category: dto.category,
    address: dto.address ?? "",
    location: toLocation(dto.lat, dto.lng),
    description: dto.description ?? "",
    imageUrl: dto.imageUrl,
    submitter: dto.submitter ?? UNKNOWN_SUBMITTER,
    submittedAt: dto.submittedAt,
    status: dto.status,
    reviewedAt: dto.reviewedAt,
    rejectReason: dto.rejectReason,
    placeId: dto.placeId,
  };
}
