import { Check } from "lucide-react";
import type { SaveResult } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { ButtonLink } from "@/shared/ui/Button";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { ImportCandidate } from "../_api/mapper";

/** 완료: 서버가 돌려준 결과 기준으로 새로 저장 / 기존 연결을 구분해서 보여줍니다. */
export function DoneStep({
  result,
  candidates,
  skipped,
  collectionIds,
}: {
  result: SaveResult;
  candidates: ImportCandidate[];
  skipped: number;
  collectionIds: string[];
}) {
  const created = result.createdPlaceIds.length;
  const linked = result.linkedPlaceIds.length;
  const placeIds = [...result.createdPlaceIds, ...result.linkedPlaceIds];
  return (
    <Screen>
      <TopBar title="저장 완료" hideBack />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[22px] font-bold">내 지도에 모았어요</h2>
        <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">
          {created > 0 && `새 장소 ${created}곳을 저장`}
          {created > 0 && linked > 0 && "하고, "}
          {linked > 0 && `기존 장소 ${linked}곳을 ${collectionIds.length ? "컬렉션에 연결" : "확인"}`}
          했어요.
        </p>
        <ul className="mt-5 space-y-3">
          {candidates.map((c) => {
            const isNew = result.createdPlaceIds.includes(c.matchedPlace!.id);
            return (
              <li key={c.tempId} className="flex items-start gap-3">
                <span className="mt-0.5 flex size-[22px] items-center justify-center rounded-full bg-selected text-primary-deep">
                  <Check size={14} strokeWidth={3} />
                </span>
                <div>
                  <p className="text-[14px] font-bold">{c.matchedPlace!.name}</p>
                  <p className="text-[12px] text-ink-sub">
                    {isNew ? "새로 저장 · 링크에서 가져옴" : collectionIds.length ? "기존 저장 · 컬렉션 연결" : "기존 저장 · 이미 내 지도에 있어요"}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
        {skipped > 0 && <p className="mt-5 text-[12px] text-ink-faint">위치를 확인하지 않은 {skipped}곳은 저장하지 않았어요.</p>}
      </div>
      <BottomCta className="space-y-2">
        <ButtonLink href={routes.map()} block replace>
          지도에서 보기
        </ButtonLink>
        <div className="grid grid-cols-2 gap-2">
          <ButtonLink href={collectionIds[0] ? routes.collection(collectionIds[0]) : routes.collections()} variant="secondary" replace>
            컬렉션으로 이동
          </ButtonLink>
          <ButtonLink href={routes.newPlan(placeIds)} variant="secondary" replace>
            일정 만들기
          </ButtonLink>
        </div>
      </BottomCta>
    </Screen>
  );
}
