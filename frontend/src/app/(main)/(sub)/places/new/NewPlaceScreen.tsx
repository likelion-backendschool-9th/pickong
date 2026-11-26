"use client";

import { MapPinPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { isStackedCluster, usePlacesInBounds, type Place, type PlaceCluster } from "@/entities/place";
import { CustomPlaceFlow } from "@/features/create-custom-place";
import { PlaceSearchPanel } from "@/features/place-search";
import { SavePlaceSheet } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import type { LatLngBounds } from "@/shared/lib/geo";
import { MapView, type LatLng } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { Skeleton } from "@/shared/ui/display";
import { Screen, TopBar } from "@/shared/ui/layout";
import { PlaceClusterSheet } from "../../../_components/PlaceClusterSheet";
import { findCluster, toClusterMarker } from "../../../_lib/clusterMarker";
import { readMapCamera } from "../../../_lib/mapSession";

/** 지도 탭에서 저장한 카메라가 없을 때(이 화면으로 바로 들어온 경우)의 기본 지역 */
const DEFAULT_CENTER: LatLng = { lat: 37.5446, lng: 127.0525 };

/**
 * 장소 직접 추가: 위에서 검색해 저장하거나, 아래 지도를 움직여 가운데 핀이 가리키는 곳을 제보합니다.
 * 제보는 관리자 승인 후에만 공개 지도에 표시됩니다. 검색으로 고른 기존 장소는 바로 저장합니다.
 * 지도는 열 때마다 지도 탭에서 마지막으로 보던 위치 · 줌에서 시작합니다.
 * 검색 결과는 상위 4개 높이까지만 보이고 나머지는 스크롤되어, 아래 지도가 계속 보입니다.
 */
export function NewPlaceScreen() {
  const router = useRouter();
  const [picked, setPicked] = useState<Place | null>(null);
  /** 가운데 핀 위치와 처음 줌. null이면 아직 세션을 읽기 전이라 지도를 만들지 않습니다. */
  const [camera, setCamera] = useState<{ center: LatLng; zoom?: number } | null>(null);
  const [bounds, setBounds] = useState<LatLngBounds | null>(null);
  const { data: nearby } = usePlacesInBounds(bounds);
  /** 목록으로 열어 둔 묶음 (확대해도 나뉘지 않는 묶음) */
  const [openCluster, setOpenCluster] = useState<PlaceCluster | null>(null);
  const [mapFocus, setMapFocus] = useState<{ key: string; position: LatLng; bounds: LatLngBounds } | null>(null);
  /** 지도에서 고른 위치 → 이름 · 설명 입력 단계 */
  const [custom, setCustom] = useState<LatLng | null>(null);
  /** 제보를 마쳤으면 화면이 숨겨질 때 입력 단계를 닫아, 다시 열 때 지도부터 시작합니다. (작성 중이면 유지) */
  const submittedRef = useRef(false);

  /**
   * cacheComponents(Activity)는 화면을 숨겨도 state를 유지하므로, 숨길 때 비우고 다시 열 때 새로 읽습니다.
   * layout effect로 읽으면 지도 탭이 숨겨지며 저장하는 마지막 카메라보다 먼저 실행되어 낡은 값을 얻습니다.
   */
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- SSR에 없는 sessionStorage를 화면이 열릴 때마다 읽어야 합니다.
    setCamera(readMapCamera() ?? { center: DEFAULT_CENTER });
    return () => {
      setCamera(null);
      if (submittedRef.current) {
        submittedRef.current = false;
        setCustom(null);
      }
    };
  }, []);

  /** 묶음은 확대하고(나뉘지 않으면 목록), 장소는 바로 저장 시트를 엽니다. */
  const selectMarker = (id: string) => {
    const cluster = findCluster(nearby.clusters, id);
    if (!cluster) setPicked(nearby.places.find((p) => p.id === id) ?? null);
    else if (isStackedCluster(cluster)) setOpenCluster(cluster);
    else setMapFocus({ key: `cluster:${cluster.id}:${Date.now()}`, position: cluster.location, bounds: cluster.bounds });
  };

  const submitReport = () => {
    sessionStorage.setItem("pickong:place-reported", "1");
    submittedRef.current = true;
    router.replace(routes.map());
  };

  if (custom) {
    return (
      <Screen>
        <TopBar title="장소 제보" onBack={() => setCustom(null)} />
        <div className="px-5 pt-2 pb-8">
          <CustomPlaceFlow location={custom} onRepick={() => setCustom(null)} mode="report" onSubmitted={submitReport} />
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
          지도를 움직여 가운데 핀을 원하는 곳에 맞추고 제보해요. 점으로 보이는 장소는 눌러서 바로 저장할 수 있어요.
        </p>
        {camera ? (
          <MapView
            className="min-h-[300px] flex-1"
            center={camera.center}
            zoom={camera.zoom}
            metersPerPixel={2.4}
            crosshair
            onCenterChange={(center) => setCamera((prev) => prev && { ...prev, center })}
            // 묶음을 눌러 확대하는 동안에는 onCenterChange가 오지 않으므로, 이동이 끝난 중심도 반영합니다.
            onCameraChange={({ center }) => setCamera((prev) => prev && { ...prev, center })}
            onBoundsChange={setBounds}
            focus={mapFocus}
            markers={[
              ...nearby.clusters.map(toClusterMarker),
              ...nearby.places.map((p) => ({ id: p.id, position: p.location, title: p.name, tone: "poi" as const })),
            ]}
            onMarkerClick={selectMarker}
            caption="가운데 핀 위치로 제보"
          />
        ) : (
          <Skeleton className="min-h-[300px] flex-1 rounded-none" />
        )}
      </div>

      <div className="sticky bottom-0 z-20 bg-bg/95 px-5 pt-3 pb-[calc(16px+env(safe-area-inset-bottom))] backdrop-blur">
        <p className="mb-2 text-center text-[12px] text-ink-faint tabular-nums">
          {camera ? `${camera.center.lat.toFixed(5)}, ${camera.center.lng.toFixed(5)}` : "\u00a0"}
        </p>
        <Button block icon={<MapPinPlus size={18} />} disabled={!camera} onClick={() => camera && setCustom(camera.center)}>
          이 위치로 장소 제보
        </Button>
      </div>

      <PlaceClusterSheet
        cluster={openCluster}
        onClose={() => setOpenCluster(null)}
        onSelect={(place) => {
          setOpenCluster(null);
          setPicked(place);
        }}
      />

      <SavePlaceSheet
        open={!!picked}
        onClose={() => setPicked(null)}
        places={picked ? [picked] : []}
        source={{ type: "SEARCH", refId: null, label: "직접 검색해서 저장" }}
      />
    </Screen>
  );
}
