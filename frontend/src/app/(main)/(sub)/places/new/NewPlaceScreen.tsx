"use client";

import { MapPinned } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useSavePlaces, type Place } from "@/entities/place";
import { CustomPlaceFlow } from "@/features/create-custom-place";
import { PlaceSearchPanel } from "@/features/place-search";
import { CollectionPicker, SavePlaceSheet } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { SectionTitle } from "@/shared/ui/display";
import { Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";

/** 장소 직접 추가: 검색해서 저장하거나, 없으면 지도에서 좌표를 지정해 개인 장소로 등록 */
export function NewPlaceScreen() {
  const router = useRouter();
  const toast = useToast();
  const save = useSavePlaces();
  const [mode, setMode] = useState<"search" | "custom">("search");
  const [picked, setPicked] = useState<Place | null>(null);
  const [collectionIds, setCollectionIds] = useState<string[]>([]);

  const saveCustom = async (place: Place) => {
    await save.mutateAsync({
      placeIds: [place.id],
      collectionIds,
      source: { type: "CUSTOM", refId: null, label: "지도에서 직접 지정" },
    });
    toast({ message: "내 지도에 저장했어요", detail: `${place.name} · 비공개 기본` });
    router.replace(routes.place(place.id));
  };

  return (
    <Screen>
      <TopBar
        title={mode === "search" ? "장소 직접 추가" : "나의 장소 추가"}
        onBack={mode === "custom" ? () => setMode("search") : undefined}
        backHref={routes.map()}
      />
      <div className="px-5 pt-2 pb-8">
        {mode === "search" ? (
          <PlaceSearchPanel
            autoFocus
            onSelect={setPicked}
            footer={
              <div className="rounded-card bg-soft p-4">
                <p className="text-[14px] font-bold">찾는 장소가 없나요?</p>
                <p className="mt-1 text-[12px] text-ink-sub">지도에서 직접 위치를 지정해 나만의 장소로 저장할 수 있어요.</p>
                <Button variant="secondary" size="md" className="mt-3 bg-bg" icon={<MapPinned size={16} />} onClick={() => setMode("custom")}>
                  지도에서 직접 지정
                </Button>
              </div>
            }
          />
        ) : (
          <CustomPlaceFlow
            onCreated={saveCustom}
            extra={
              <div>
                <SectionTitle className="mb-2">저장할 컬렉션</SectionTitle>
                <CollectionPicker selected={collectionIds} onChange={setCollectionIds} />
              </div>
            }
          />
        )}
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
