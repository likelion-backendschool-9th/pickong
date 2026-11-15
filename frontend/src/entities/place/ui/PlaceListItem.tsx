import type { ReactNode } from "react";
import { Thumb } from "@/shared/ui/display";
import { categoryLabel } from "../categories";
import type { Place } from "../model";

/** 장소 목록 한 줄: 썸네일 · 이름 · 지역/카테고리 */
export function PlaceListItem({
  place,
  meta,
  trailing,
  onClick,
}: {
  place: Place;
  meta?: ReactNode;
  trailing?: ReactNode;
  onClick?: () => void;
}) {
  const body = (
    <>
      <Thumb src={place.imageUrl} alt="" className="size-14" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[14px] font-bold text-ink">{place.name}</div>
        <div className="mt-0.5 truncate text-[12px] text-ink-sub">
          {place.region} · {categoryLabel(place.category)}
        </div>
        {meta && <div className="mt-0.5 truncate text-[12px] text-ink-faint">{meta}</div>}
      </div>
    </>
  );
  return (
    <div className="flex items-center gap-3 py-2">
      {onClick ? (
        <button type="button" onClick={onClick} className="flex min-w-0 flex-1 items-center gap-3 text-left">
          {body}
        </button>
      ) : (
        <div className="flex min-w-0 flex-1 items-center gap-3">{body}</div>
      )}
      {trailing}
    </div>
  );
}
