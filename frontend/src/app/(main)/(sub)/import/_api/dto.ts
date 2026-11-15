import type { PlaceDto } from "@/entities/place";

export interface ImportCandidateDto {
  tempId: string;
  extractedName: string;
  extractedAddress: string;
  matchedPlace: PlaceDto | null;
  alternatives: PlaceDto[];
  confidence: number;
  needsReview: boolean;
  duplicateOf: string | null;
  alreadySaved: boolean;
}

export interface ImportJobDto {
  id: string;
  source: { type: "URL" | "TEXT"; value: string };
  status: "RUNNING" | "DONE" | "FAILED";
  step: number;
  totalSteps: number;
  failureReason: string | null;
  candidates: ImportCandidateDto[];
}
