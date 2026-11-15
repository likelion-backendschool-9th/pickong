import { ChevronRight, MapPin, MapPinOff } from "lucide-react";
import type { Place } from "../model";

/** 사진 아래 장소 정보 바. 장소가 없는 사진은 비활성 상태로 표시합니다. */
export function PlaceInfoBar({ place, onClick }: { place: Place | null; onClick?: () => void }) {
  if (!place) {
    return (
      <div className="flex h-[46px] items-center gap-2 px-4 text-[13px] text-ink-faint" aria-disabled="true">
        <MapPinOff size={16} strokeWidth={1.6} />
        <span>이 사진에는 연결된 장소가 없어요</span>
      </div>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-[46px] w-full items-center gap-2 px-4 text-left active:bg-fill"
      aria-label={`${place.name} 지도에서 보기`}
    >
      <MapPin size={16} strokeWidth={1.8} className="shrink-0 text-primary-deep" />
      <span className="truncate text-[13px] font-bold">{place.name}</span>
      <span className="shrink-0 text-[12px] text-ink-faint">· {place.region}</span>
      <ChevronRight size={18} strokeWidth={1.6} className="ml-auto shrink-0 text-ink-faint" />
    </button>
  );
}
