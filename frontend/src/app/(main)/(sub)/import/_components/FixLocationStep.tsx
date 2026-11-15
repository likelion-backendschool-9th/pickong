"use client";

import { MapPinned } from "lucide-react";
import { useState } from "react";
import type { Place } from "@/entities/place";
import { CustomPlaceFlow } from "@/features/create-custom-place";
import { PlaceSearchPanel } from "@/features/place-search";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { SelectRow } from "@/shared/ui/controls";
import { SectionTitle } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import type { ImportCandidate } from "../_api/mapper";

/** 위치가 불명확한 후보를 사용자가 직접 확인합니다. 확인 전에는 저장되지 않습니다. */
export function FixLocationStep({
  candidate,
  onBack,
  onApply,
  onExclude,
}: {
  candidate: ImportCandidate;
  onBack: () => void;
  onApply: (place: Place) => void;
  onExclude: () => void;
}) {
  const [options, setOptions] = useState<Place[]>(
    candidate.matchedPlace ? [candidate.matchedPlace, ...candidate.alternatives] : candidate.alternatives,
  );
  const [picked, setPicked] = useState<Place | null>(candidate.matchedPlace);
  const [sheet, setSheet] = useState<"search" | "custom" | null>(null);

  const choose = (place: Place) => {
    if (!options.some((o) => o.id === place.id)) setOptions((o) => [place, ...o]);
    setPicked(place);
    setSheet(null);
  };

  return (
    <Screen>
      <TopBar title="장소 위치 확인" onBack={onBack} />
      <div className="px-5 pt-2 pb-6">
        <h2 className="text-[22px] font-bold">{candidate.extractedName}</h2>
        <p className="mt-1.5 text-[13px] text-ink-sub">원문에 주소가 없어 정확한 위치를 찾지 못했어요. 맞는 장소를 직접 선택해 주세요.</p>

        <SectionTitle className="mt-6 mb-1">비슷한 장소</SectionTitle>
        <div className="-mx-3">
          {options.map((p) => (
            <SelectRow
              key={p.id}
              type="radio"
              checked={picked?.id === p.id}
              onToggle={() => setPicked(p)}
              title={p.name}
              description={p.address}
              highlight
            />
          ))}
        </div>
        <div className="mt-2 flex flex-wrap gap-x-4">
          <button type="button" onClick={() => setSheet("search")} className="h-10 text-[13px] font-bold text-primary-deep">
            다른 장소 검색 →
          </button>
          <button type="button" onClick={() => setSheet("custom")} className="flex h-10 items-center gap-1 text-[13px] font-bold text-primary-deep">
            <MapPinned size={15} /> 지도에서 직접 지정하기 →
          </button>
        </div>

        {picked && (
          <MapView className="-mx-5 mt-4 h-44" markers={[{ id: picked.id, position: picked.location, title: picked.name }]} selectedId={picked.id} />
        )}
        <p className="mt-3 text-[12px] text-ink-faint">확인하지 않으면 이 장소는 저장되지 않아요.</p>
      </div>
      <BottomCta className="space-y-2">
        <Button block disabled={!picked} onClick={() => picked && onApply(picked)}>
          선택한 위치 적용
        </Button>
        <Button block variant="ghost" onClick={onExclude}>
          이 장소 제외
        </Button>
      </BottomCta>

      <BottomSheet open={sheet === "search"} onClose={() => setSheet(null)} title="장소 검색" className="h-[85dvh]">
        {sheet === "search" && <PlaceSearchPanel autoFocus initialQuery={candidate.extractedName.replace("노을 ", "")} onSelect={choose} />}
      </BottomSheet>
      <BottomSheet open={sheet === "custom"} onClose={() => setSheet(null)} title="지도에서 직접 지정" className="h-[92dvh]">
        {sheet === "custom" && <CustomPlaceFlow initialName={candidate.extractedName} submitLabel="이 위치로 확인" onCreated={choose} />}
      </BottomSheet>
    </Screen>
  );
}
