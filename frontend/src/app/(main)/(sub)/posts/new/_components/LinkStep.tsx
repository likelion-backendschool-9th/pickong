"use client";

import { ChevronLeft, ChevronRight, MapPin, MapPinned, Repeat2, Search, X } from "lucide-react";
import { useState } from "react";
import type { Place } from "@/entities/place";
import { CustomPlaceFlow } from "@/features/create-custom-place";
import { PlaceSearchPanel } from "@/features/place-search";
import { cn } from "@/shared/lib/cn";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Photo } from "@/shared/ui/display";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { movePhoto, uniqueDraftPlaces, type DraftPhoto } from "../_lib/draft";

/** 사진별 장소 연결: 같은 장소를 여러 사진에 연결할 때 반복 검색을 줄입니다. */
export function LinkStep({
  photos,
  onChange,
  suggestedPlaces,
  onBack,
  onNext,
}: {
  photos: DraftPhoto[];
  onChange: (photos: DraftPhoto[]) => void;
  suggestedPlaces: Place[];
  onBack: () => void;
  onNext: () => void;
}) {
  const [index, setIndex] = useState(0);
  const [sheet, setSheet] = useState<"search" | "custom" | null>(null);
  const current = photos[index];
  const previousPlace = index > 0 ? photos[index - 1].place : null;

  const setPlace = (place: Place | null) => {
    onChange(photos.map((p, i) => (i === index ? { ...p, place } : p)));
    setSheet(null);
  };
  const move = (dir: -1 | 1) => {
    onChange(movePhoto(photos, index, index + dir));
    setIndex(index + dir);
  };

  // 최근 사용: 이 게시물의 다른 사진에 연결한 장소 + 방문 기록 장소
  const recent = [
    ...photos
      .map((p, i) => ({ p, i }))
      .filter(({ p, i }) => p.place && i !== index)
      .reduce<{ place: Place; note: string }[]>((acc, { p, i }) => {
        const found = acc.find((a) => a.place.id === p.place!.id);
        if (found) found.note += `, ${i + 1}`;
        else acc.push({ place: p.place!, note: `사진 ${i + 1}` });
        return acc;
      }, [])
      .map((r) => ({ ...r, note: `${r.note}에서 사용` })),
    ...suggestedPlaces
      .filter((sp) => !photos.some((p) => p.place?.id === sp.id))
      .map((place) => ({ place, note: "방문 기록에서" })),
  ];

  const linked = uniqueDraftPlaces(photos).length;
  const noPlace = photos.filter((p) => !p.place).length;

  return (
    <Screen>
      <TopBar title="사진별 장소 연결" onBack={onBack} />
      <div className="px-5">
        <p className="text-[15px] font-bold">
          사진 {index + 1} / {photos.length}
        </p>
        <p className="mt-0.5 text-[12px] text-ink-sub">사진마다 다른 장소를 연결할 수 있어요.</p>
      </div>
      <div className="relative mt-3 aspect-[13/10] w-full bg-fill">
        <Photo src={current.url} alt={`사진 ${index + 1}`} priority />
        <div className="absolute right-3 bottom-3 flex gap-1.5">
          <button type="button" aria-label="순서 앞으로" disabled={index === 0} onClick={() => move(-1)} className="flex size-9 items-center justify-center rounded-full bg-white/90 disabled:opacity-40">
            <ChevronLeft size={18} />
          </button>
          <button type="button" aria-label="순서 뒤로" disabled={index === photos.length - 1} onClick={() => move(1)} className="flex size-9 items-center justify-center rounded-full bg-white/90 disabled:opacity-40">
            <ChevronRight size={18} />
          </button>
        </div>
      </div>

      <div className="scrollbar-none flex gap-2 overflow-x-auto px-5 py-3" role="tablist" aria-label="사진 목록">
        {photos.map((p, i) => (
          <button
            key={p.key}
            type="button"
            role="tab"
            aria-selected={i === index}
            aria-label={`사진 ${i + 1}${p.place ? ` · ${p.place.name}` : " · 장소 없음"}`}
            onClick={() => setIndex(i)}
            className={cn("relative size-14 shrink-0 overflow-hidden rounded-lg", i === index ? "ring-[3px] ring-primary-deep" : "opacity-80")}
          >
            <Photo src={p.url} alt="" sizes="56px" />
            <span className="absolute bottom-0.5 left-0.5 rounded bg-black/55 px-1 text-[10px] font-bold text-white">{i + 1}</span>
            {p.place && <MapPin size={14} className="absolute top-0.5 right-0.5 fill-primary text-on-primary" />}
          </button>
        ))}
      </div>

      <div className="px-5 pb-4">
        <h3 className="text-[13px] font-bold">사진 {index + 1}의 장소</h3>
        {current.place ? (
          <div className="mt-2 flex items-center gap-2 rounded-card bg-soft px-4 py-3">
            <MapPin size={18} className="shrink-0 text-primary-deep" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[14px] font-bold">{current.place.name}</div>
              <div className="text-[12px] text-ink-sub">{current.place.region}</div>
            </div>
            <button type="button" onClick={() => setSheet("search")} className="h-9 px-2 text-[13px] font-bold text-primary-deep">
              변경
            </button>
            <button type="button" aria-label="연결 해제" onClick={() => setPlace(null)} className="flex size-9 items-center justify-center text-ink-faint">
              <X size={18} />
            </button>
          </div>
        ) : (
          <p className="mt-1 text-[12px] text-ink-faint">아직 연결된 장소가 없어요. 장소 없이 게시할 수도 있어요.</p>
        )}

        <div className="mt-3 space-y-2">
          {previousPlace && previousPlace.id !== current.place?.id && (
            <Button variant="secondary" block size="md" icon={<Repeat2 size={16} />} onClick={() => setPlace(previousPlace)}>
              이전 사진과 같은 장소 사용 · {previousPlace.name}
            </Button>
          )}
          <Button variant="outline" block size="md" icon={<Search size={16} />} onClick={() => setSheet("search")}>
            장소 검색 / 지도에서 지정
          </Button>
        </div>
        <p className="mt-4 text-[12px] text-ink-faint">
          연결한 장소 {linked}곳 · 장소 없는 사진 {noPlace}장
        </p>
      </div>

      <BottomCta>
        <Button block onClick={onNext}>
          다음 · 게시물 작성
        </Button>
      </BottomCta>

      <BottomSheet open={sheet === "search"} onClose={() => setSheet(null)} title={`사진 ${index + 1}에 연결할 장소`} className="h-[85dvh]">
        {sheet === "search" && (
          <PlaceSearchPanel
            autoFocus
            recent={recent}
            onSelect={setPlace}
            footer={
              <button type="button" onClick={() => setSheet("custom")} className="flex h-11 items-center gap-2 text-[14px] font-bold text-primary-deep">
                <MapPinned size={17} /> 지도에서 직접 지정 →
              </button>
            }
          />
        )}
      </BottomSheet>
      <BottomSheet open={sheet === "custom"} onClose={() => setSheet(null)} title="지도에서 직접 지정" className="h-[92dvh]">
        {sheet === "custom" && <CustomPlaceFlow onCreated={(place) => setPlace(place)} submitLabel="이 장소 연결" />}
      </BottomSheet>
    </Screen>
  );
}
