import type { PlaceCategory } from "@/entities/place";
import type { PlaceSubmissionStatus } from "../model";

export interface PlaceSubmissionDto {
  id: string;
  name: string;
  category: PlaceCategory;
  address: string | null;
  lat: number | null;
  lng: number | null;
  description: string | null;
  imageUrl: string | null;
  submitter: { id: string; username: string; nickname: string } | null;
  submittedAt: string;
  status: PlaceSubmissionStatus;
  reviewedAt: string | null;
  rejectReason: string | null;
  placeId: string | null;
}
