"use client";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { categoryLabel } from "@/entities/place";
import { useFeedPlaces } from "@/entities/post";
import { SavePlaceButton } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { Skeleton, Thumb } from "@/shared/ui/display";

/**
 * 피드 지도: 공개 게시물에 연결된 장소를 탐색합니다.
 * 내가 저장한 장소를 관리하는 ‘내 지도’와는 다른 데이터입니다.
 */
export function FeedMap() {
  const { data, isPending } = useFeedPlaces();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = data?.find((d) => d.place.id === selectedId) ?? null;

  if (isPending) return <Skeleton className="h-[calc(100dvh-190px)] rounded-none" />;

  return (
    <div className="relative">
      <div className="absolute top-3 left-3 z-10 rounded-full bg-white/95 px-3 py-1.5 text-[12px] font-bold text-ink shadow-sm">
        피드 지도 · 공개 게시물의 장소 {data?.length ?? 0}곳
      </div>
      <MapView
        className="h-[calc(100dvh-190px)] min-h-[420px]"
        markers={(data ?? []).map((d) => ({
          id: d.place.id,
          position: d.place.location,
          label: String(d.postCount),
          title: d.place.name,
        }))}
        selectedId={selectedId}
        onMarkerClick={setSelectedId}
      />
      {selected && (
        <div className="absolute inset-x-3 bottom-3 z-10 flex items-center gap-3 rounded-card bg-bg p-3 shadow-[0_2px_12px_rgba(0,0,0,0.12)]">
          <Thumb src={selected.place.imageUrl} alt="" className="size-14" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-[15px] font-bold">{selected.place.name}</div>
            <div className="text-[12px] text-ink-sub">
              {selected.place.region} · {categoryLabel(selected.place.category)}
            </div>
            <Link href={routes.post(selected.postIds[0])} className="mt-1 inline-flex items-center text-[12px] font-bold text-primary-deep">
              게시물 {selected.postCount}개 보기 <ChevronRight size={14} />
            </Link>
          </div>
          <SavePlaceButton place={selected.place} withLabel source={{ type: "POST", refId: selected.postIds[0], label: "피드 지도에서 발견" }} />
        </div>
      )}
    </div>
  );
}
