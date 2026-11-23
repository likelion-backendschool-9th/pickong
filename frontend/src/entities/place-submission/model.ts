import type { PlaceCategory } from "@/entities/place";
import type { LatLng } from "@/shared/lib/geo";

export type PlaceSubmissionStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface PlaceSubmissionSubmitter {
  id: string;
  username: string;
  nickname: string;
}

/** 사용자가 직접 등록해 관리자 심사를 기다리는 장소 제보. 공개 장소(Place)와는 별개입니다. */
export interface PlaceSubmission {
  id: string;
  name: string;
  category: PlaceCategory;
  address: string;
  location: LatLng | null;
  description: string;
  imageUrl: string | null;
  submitter: PlaceSubmissionSubmitter;
  submittedAt: string;
  status: PlaceSubmissionStatus;
  reviewedAt: string | null;
  rejectReason: string | null;
  /** 승인 후 공개 장소로 만들어진 id */
  placeId: string | null;
}
