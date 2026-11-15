"use client";

import { ChevronRight, Link2, MapPinPlus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { categoryLabel, type SavedPlace } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { Badge, Thumb } from "@/shared/ui/display";

export function SavedMapView({ saved }: { saved: SavedPlace[] }) {
  const [selectedId, setSelectedId] = useState<string | null>(saved[0]?.id ?? null);
  const [addOpen, setAddOpen] = useState(false);
  const selected = saved.find((s) => s.id === selectedId) ?? null;

  return (
    <div className="relative">
      <MapView
        className="h-[calc(100dvh-260px)] min-h-[440px]"
        markers={saved.map((s, i) => ({
          id: s.id,
          position: s.place.location,
          label: String(i + 1),
          title: s.place.name,
          tone: s.visitStatus === "VISITED" ? "muted" : "primary",
        }))}
        selectedId={selectedId}
        onMarkerClick={setSelectedId}
        caption={saved.length === 0 ? "조건에 맞는 장소가 없어요" : "예시 지도 · 내가 저장한 장소만 표시"}
      />

      {selected && (
        <div className="absolute inset-x-3 bottom-3 z-10 rounded-card bg-bg p-4 shadow-[0_2px_16px_rgba(0,0,0,0.12)]">
          <Link href={routes.place(selected.place.id)} className="flex items-center gap-3">
            <Thumb src={selected.place.imageUrl} alt="" className="size-14" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-[16px] font-bold">{selected.place.name}</span>
                {selected.visitStatus === "VISITED" && <Badge tone="neutral">방문 완료</Badge>}
              </div>
              <div className="text-[12px] text-ink-sub">
                {selected.place.region} · {categoryLabel(selected.place.category)}
              </div>
              <div className="mt-0.5 truncate text-[12px] text-ink-faint">{selected.source.label}</div>
            </div>
            <ChevronRight size={18} className="text-ink-faint" aria-label="장소 상세 보기" />
          </Link>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <ButtonLink href={routes.newPlan([selected.place.id])} size="md">
              일정에 추가
            </ButtonLink>
            <Button size="md" variant="secondary" onClick={() => setAddOpen(true)}>
              직접 추가 / 링크
            </Button>
          </div>
        </div>
      )}

      <BottomSheet open={addOpen} onClose={() => setAddOpen(false)} title="장소 추가하기">
        <div className="space-y-2 pb-[calc(8px+env(safe-area-inset-bottom))]">
          <ButtonLink href={routes.newPlace()} variant="secondary" block icon={<MapPinPlus size={18} />}>
            장소 직접 추가
          </ButtonLink>
          <ButtonLink href={routes.importLink()} variant="secondary" block icon={<Link2 size={18} />}>
            AI 링크로 가져오기
          </ButtonLink>
        </div>
      </BottomSheet>
    </div>
  );
}
