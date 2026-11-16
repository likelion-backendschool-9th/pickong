"use client";

import { SlidersHorizontal } from "lucide-react";
import { CATEGORY_LABEL, type PlaceCategory } from "@/entities/place";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Chip, Switch } from "@/shared/ui/controls";

export interface MapFilterValue {
  savedOnly: boolean;
  category: PlaceCategory | "ALL";
}

/** 적용 중인 필터 수 (필터 버튼의 ‘필터 2’ 표시) */
export const activeFilterCount = (v: MapFilterValue) => (v.savedOnly ? 1 : 0) + (v.category !== "ALL" ? 1 : 0);

/**
 * 내 지도 필터 패널: ‘저장한 곳만’ · 카테고리. 고르는 즉시 지도에 반영됩니다.
 * 상단을 가볍게 하려고 화면에서 빼서 필터 버튼 안으로 옮겼습니다.
 */
export function MapFilterSheet({
  open,
  onClose,
  value,
  onChange,
  categories,
  onEditCategories,
}: {
  open: boolean;
  onClose: () => void;
  value: MapFilterValue;
  onChange: (patch: Partial<MapFilterValue>) => void;
  /** 내 카테고리 칩 설정 (보이는 것 · 순서) */
  categories: PlaceCategory[];
  onEditCategories: () => void;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title="필터">
      <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
        <div className="flex items-center justify-between border-b border-line pb-3">
          <span className="min-w-0">
            <span className="block text-[15px] font-bold">저장한 곳만 보기</span>
            <span className="block text-[12px] text-ink-sub">검색과 지도에 내가 저장한 장소만 보여요</span>
          </span>
          <Switch label="저장한 곳만 보기" hideLabel checked={value.savedOnly} onChange={(savedOnly) => onChange({ savedOnly })} className="shrink-0" />
        </div>

        <div className="mt-4 flex items-center justify-between">
          <span className="text-[15px] font-bold">카테고리</span>
          <button type="button" onClick={onEditCategories} className="inline-flex h-8 items-center gap-1 rounded-full px-2 text-[12px] font-bold text-primary-deep active:bg-accent-soft">
            <SlidersHorizontal size={14} /> 칩 편집
          </button>
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          <Chip selected={value.category === "ALL"} onClick={() => onChange({ category: "ALL" })}>
            전체
          </Chip>
          {categories.map((c) => (
            <Chip key={c} selected={value.category === c} onClick={() => onChange({ category: c })}>
              {CATEGORY_LABEL[c]}
            </Chip>
          ))}
        </div>

        <div className="mt-6 grid grid-cols-[auto_1fr] gap-2">
          <Button variant="ghost" size="md" disabled={activeFilterCount(value) === 0} onClick={() => onChange({ savedOnly: false, category: "ALL" })}>
            초기화
          </Button>
          <Button size="md" onClick={onClose}>
            완료
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}
