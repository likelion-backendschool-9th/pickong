"use client";

import { categoryLabel, formatHours, type Place } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";

/** 장소 지도 미리보기. 닫으면 보고 있던 피드·사진 상태로 돌아갑니다. */
export function PlaceMapSheet({
  place,
  onClose,
  onSave,
}: {
  place: Place | null;
  onClose: () => void;
  onSave: (place: Place) => void;
}) {
  return (
    <BottomSheet
      open={!!place}
      onClose={onClose}
      title={place?.name}
      description={place ? `${place.address} · ${categoryLabel(place.category)}` : undefined}
    >
      {place && (
        <div className="pb-[env(safe-area-inset-bottom)]">
          <MapView
            className="-mx-5 h-56"
            markers={[{ id: place.id, position: place.location, title: place.name }]}
            selectedId={place.id}
          />
          <p className="mt-3 text-[12px] text-ink-sub">영업시간 {formatHours(place.businessHours)} · 지도를 움직이거나 확대해 주변을 확인할 수 있어요.</p>
          <div className="mt-4 mb-3 flex flex-col gap-2">
            <ButtonLink href={routes.place(place.id)} variant="secondary" block>
              장소 상세 보기
            </ButtonLink>
            <Button block onClick={() => onSave(place)}>
              찜콩하기
            </Button>
          </div>
        </div>
      )}
    </BottomSheet>
  );
}
