"use client";

import { MapPinPlus, MapPinCheck } from "lucide-react";
import { useState } from "react";
import { useSavedPlaces, type Place, type SavePlacesInput } from "@/entities/place";
import { cn } from "@/shared/lib/cn";
import { SavePlaceSheet } from "./SavePlaceSheet";

/** 장소 하나를 찜콩하는 아이콘 버튼 (목록·지도 카드용) */
export function SavePlaceButton({
  place,
  source,
  withLabel,
  className,
}: {
  place: Place;
  source?: SavePlacesInput["source"];
  withLabel?: boolean;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const { data: saved } = useSavedPlaces();
  const isSaved = !!saved?.some((s) => s.place.id === place.id);
  const Icon = isSaved ? MapPinCheck : MapPinPlus;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={isSaved ? `${place.name} 찜콩됨 · 컬렉션 변경` : `${place.name} 찜콩하기`}
        className={cn(
          "inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1 rounded-full px-2 text-[12px] font-bold active:bg-fill",
          isSaved ? "text-primary-deep" : "text-ink",
          className,
        )}
      >
        <Icon size={22} strokeWidth={1.6} />
        {withLabel && <span>{isSaved ? "찜콩됨" : "찜콩"}</span>}
      </button>
      <SavePlaceSheet open={open} onClose={() => setOpen(false)} places={[place]} source={source} />
    </>
  );
}
