"use client";

import { MapPinPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { usePlacesInBounds, useSavePlaces, type Place } from "@/entities/place";
import { CustomPlaceFlow } from "@/features/create-custom-place";
import { PlaceSearchPanel } from "@/features/place-search";
import { CollectionPicker, SavePlaceSheet } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import type { LatLngBounds } from "@/shared/lib/geo";
import { MapView, type LatLng } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { SectionTitle } from "@/shared/ui/display";
import { Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";

const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };

/**
 * 장소 직접 추가: 위에서 검색해 저장하거나, 아래 지도를 움직여 가운데 핀이 가리키는 곳을 나만의 장소로 등록합니다.
 * 검색 결과는 상위 4개 높이까지만 보이고 나머지는 스크롤되어, 아래 지도가 계속 보입니다.
 * 지도의 주변 장소 점을 누르면 그 장소를 바로 저장할 수 있습니다.
 */
export function NewPlaceScreen() {
  const router = useRouter();
  const toast = useToast();
  const save = useSavePlaces();
  const [picked, setPicked] = useState<Place | null>(null);
  const [collectionIds, setCollectionIds] = useState<string[]>([]);
  const [center, setCenter] = useState<LatLng>(DEFAULT_CENTER);
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const { data: nearby = [] } = usePlacesInBounds(bounds);
  /** 지도에서 고른 위치 → 이름 · 설명 입력 단계 */
  const [custom, setCustom] = useState<LatLng | null>(null);

  const saveCustom = async (place: Place) => {
    await save.mutateAsync({
      placeIds: [place.id],
      collectionIds,
      source: { type: "CUSTOM", refId: null, label: "지도에서 직접 지정" },
    });
    toast({ message: "내 지도에 저장했어요", detail: `${place.name} · 비공개 기본` });
    router.replace(routes.place(place.id));
  };

  if (custom) {
    return (
      <Screen>
        <TopBar title="나의 장소 추가" onBack={() => setCustom(null)} />
        <div className="px-5 pt-2 pb-8">
          <CustomPlaceFlow
            location={custom}
            onRepick={() => setCustom(null)}
            onCreated={saveCustom}
            extra={
              <div>
                <SectionTitle className="mb-2">저장할 컬렉션</SectionTitle>
                <CollectionPicker selected={collectionIds} onChange={setCollectionIds} />
              </div>
            }
          />
        </div>
      </Screen>
    );
  }

  return (
    <Screen>
      <TopBar title="장소 직접 추가" backHref={routes.map()} />
      <div className="px-5 pt-2">
        <PlaceSearchPanel autoFocus compactResults onSelect={setPicked} />
      </div>

      <div className="mt-3 flex flex-1 flex-col">
        <p className="px-5 pb-2 text-[12px] text-ink-sub">
          지도를 움직여 가운데 핀을 원하는 곳에 맞추고 추가해요. 점으로 보이는 장소는 눌러서 바로 저장할 수 있어요.
        </p>
        <MapView
          className="min-h-[300px] flex-1"
          center={center}
          metersPerPixel={2.4}
          crosshair
          onCenterChange={setCenter}
          onBoundsChange={setBounds}
          markers={nearby.map((p) => ({ id: p.id, position: p.location, title: p.name, tone: "poi" as const }))}
          onMarkerClick={(id) => setPicked(nearby.find((p) => p.id === id) ?? null)}
          caption="예시 지도 · 가운데 핀 위치로 추가"
        />
      </div>

      <div className="sticky bottom-0 z-20 bg-bg/95 px-5 pt-3 pb-[calc(16px+env(safe-area-inset-bottom))] backdrop-blur">
        <p className="mb-2 text-center text-[12px] text-ink-faint tabular-nums">
          {center.lat.toFixed(5)}, {center.lng.toFixed(5)}
        </p>
        <Button block icon={<MapPinPlus size={18} />} onClick={() => setCustom(center)}>
          이 위치에 장소 추가
        </Button>
      </div>

      <SavePlaceSheet
        open={!!picked}
        onClose={() => setPicked(null)}
        places={picked ? [picked] : []}
        source={{ type: "SEARCH", refId: null, label: "직접 검색해서 저장" }}
      />
    </Screen>
  );
}
