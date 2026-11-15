import type { SaveResult } from "@/entities/place";

/** 같은 장소가 여러 사진에 연결돼 있어도 한 번만 저장합니다. */
export function uniquePlaceIds(ids: (string | null | undefined)[]): string[] {
  return [...new Set(ids.filter((id): id is string => !!id))];
}

/** 저장 결과 문구. 새로 저장한 장소와 기존 장소를 컬렉션에 연결한 결과를 구분합니다. */
export function describeSaveResult(result: SaveResult): { message: string; detail: string } {
  const created = result.createdPlaceIds.length;
  const linked = result.linkedPlaceIds.length;
  const total = created + linked;
  const parts = [created > 0 && `새로 저장 ${created}곳`, linked > 0 && `기존 장소 ${linked}곳 연결`].filter(Boolean);
  return {
    message: total === 1 ? (created ? "내 지도에 저장했어요" : "컬렉션에 연결했어요") : `장소 ${total}곳 정리 완료`,
    detail: parts.join(" · "),
  };
}
