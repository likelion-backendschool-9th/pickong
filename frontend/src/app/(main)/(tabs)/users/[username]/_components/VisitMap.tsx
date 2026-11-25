"use client";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { categoryLabel } from "@/entities/place";
import { useUserPlaces } from "@/entities/post";
import type { User } from "@/entities/user";
import { SavePlaceButton } from "@/features/save-place";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { EmptyState, Skeleton, Thumb } from "@/shared/ui/display";

/**
 * 방문 지도: 이 사용자가 게시물에 연결한 장소를 지도에 띄웁니다.
 * 저장한 장소(간 곳 · 갈 곳)를 모두 모아 두는 ‘내 지도’와 달리, 게시물로 공유한 장소만 보여 줍니다.
 */
export function VisitMap({ user }: { user: User }) {
  const { data, isPending } = useUserPlaces(user.username);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = data?.find((d) => d.place.id === selectedId) ?? null;

  if (isPending) return <Skeleton className="m-5 h-[440px]" />;
  if (!data?.length) {
    return (
      <EmptyState
        title="아직 방문 지도에 장소가 없어요"
        description={user.isMe ? "게시물 사진에 장소를 연결하면 여기에 쌓여요." : undefined}
      />
    );
  }

  return (
    <div className="relative">
      <div className="absolute top-3 left-3 z-10 rounded-full bg-white/95 px-3 py-1.5 text-[12px] font-bold text-ink shadow-sm">
        게시물에 남긴 장소 {data.length}곳
      </div>
      <MapView
        className="h-[440px]"
        markers={data.map((d) => ({
          id: d.place.id,
          position: d.place.location,
          label: String(d.postCount),
          title: d.place.name,
        }))}
        selectedId={selectedId}
        onMarkerClick={setSelectedId}
        ariaLabel={`${user.username}님의 방문 지도`}
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
          {!user.isMe && (
            <SavePlaceButton
              place={selected.place}
              withLabel
              source={{ type: "POST", refId: selected.postIds[0], label: `${user.username}님의 방문 지도에서 발견` }}
            />
          )}
        </div>
      )}
    </div>
  );
}
