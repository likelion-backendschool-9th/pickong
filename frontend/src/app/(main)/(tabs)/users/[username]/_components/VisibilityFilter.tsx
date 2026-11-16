"use client";

import { ChevronDown, ListFilter } from "lucide-react";
import type { Visibility } from "@/shared/config/visibility";
import { Dropdown, type DropdownOption } from "@/shared/ui/Dropdown";
import { VisibilityIcon } from "@/shared/ui/VisibilityIcon";

export type VisibilityFilterValue = "ALL" | Visibility;

const OPTIONS: DropdownOption<VisibilityFilterValue>[] = [
  { value: "ALL", label: "전체 보기", icon: <ListFilter size={15} /> },
  { value: "PUBLIC", label: "전체 공개", icon: <VisibilityIcon visibility="PUBLIC" size={15} aria-hidden /> },
  { value: "FOLLOWERS", label: "팔로우 공개", icon: <VisibilityIcon visibility="FOLLOWERS" size={15} aria-hidden /> },
  { value: "PRIVATE", label: "나만 보기", icon: <VisibilityIcon visibility="PRIVATE" size={15} aria-hidden /> },
];

export const parseVisibilityFilter = (raw: string | null): VisibilityFilterValue =>
  OPTIONS.find((o) => o.value === raw)?.value ?? "ALL";

export const visibilityFilterLabel = (v: VisibilityFilterValue) => OPTIONS.find((o) => o.value === v)!.label;

export const matchesVisibility = (filter: VisibilityFilterValue, visibility: Visibility) =>
  filter === "ALL" || filter === visibility;

/** 내 프로필 게시물 · 컬렉션 탭의 공개 범위 필터 (탭 줄 오른쪽 끝) */
export function VisibilityFilter({ value, onChange }: { value: VisibilityFilterValue; onChange: (v: VisibilityFilterValue) => void }) {
  const active = value !== "ALL";
  return (
    <Dropdown
      value={value}
      options={OPTIONS}
      onChange={onChange}
      ariaLabel={`공개 범위로 보기: ${visibilityFilterLabel(value)}`}
      trigger={
        <span className={active ? "inline-flex items-center gap-0.5 text-primary-deep" : "inline-flex items-center gap-0.5 text-ink-sub"}>
          {active ? <VisibilityIcon visibility={value} size={17} aria-hidden /> : <ListFilter size={17} aria-hidden />}
          <ChevronDown size={14} aria-hidden />
        </span>
      }
    />
  );
}
