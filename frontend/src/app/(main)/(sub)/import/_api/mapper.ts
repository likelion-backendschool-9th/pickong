import { toPlace, type Place } from "@/entities/place";
import type { ImportCandidateDto, ImportJobDto } from "./dto";

/** 링크에서 추출한 장소 후보 (이 페이지에서만 사용하므로 entities가 아닌 페이지 옆에 둡니다) */
export interface ImportCandidate {
  tempId: string;
  extractedName: string;
  extractedAddress: string;
  matchedPlace: Place | null;
  alternatives: Place[];
  confidence: number;
  /** 위치가 불명확해서 사용자 확인이 필요한 후보. 자동으로 선택·저장하지 않습니다. */
  needsReview: boolean;
  /** 같은 링크 안에서 중복으로 언급된 후보 → 목록에서 제외 */
  duplicateOf: string | null;
  /** 이미 내 지도에 있는 장소 → 새로 만들지 않고 컬렉션에만 연결 */
  alreadySaved: boolean;
}

export interface ImportJob {
  id: string;
  source: { type: "URL" | "TEXT"; value: string };
  status: "RUNNING" | "DONE" | "FAILED";
  step: number;
  totalSteps: number;
  failureReason: string | null;
  candidates: ImportCandidate[];
}

const toCandidate = (dto: ImportCandidateDto): ImportCandidate => ({
  ...dto,
  matchedPlace: dto.matchedPlace ? toPlace(dto.matchedPlace) : null,
  alternatives: dto.alternatives.map(toPlace),
});

export const toImportJob = (dto: ImportJobDto): ImportJob => ({ ...dto, candidates: dto.candidates.map(toCandidate) });
