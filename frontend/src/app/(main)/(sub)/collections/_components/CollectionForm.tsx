"use client";

import { useState } from "react";
import { PIN_COLORS, type PinColor } from "@/entities/collection";
import type { Visibility } from "@/shared/config/visibility";
import { visibilityLabel } from "@/shared/config/visibility";
import { cn } from "@/shared/lib/cn";
import { Field, Input, SelectTrigger, Textarea } from "@/shared/ui/form";
import { VisibilitySheet } from "@/shared/ui/VisibilitySheet";

export interface CollectionFormValue {
  title: string;
  description: string;
  visibility: Visibility;
  pinColor: PinColor;
}

const PIN_SWATCH: Record<PinColor, string> = {
  LIME: "bg-primary",
  GREEN: "bg-primary-deep",
  BROWN: "bg-[#8a6a4f]",
};

/** 컬렉션 만들기·편집 공용 폼 */
export function CollectionForm({ value, onChange }: { value: CollectionFormValue; onChange: (v: CollectionFormValue) => void }) {
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  const set = (patch: Partial<CollectionFormValue>) => onChange({ ...value, ...patch });

  return (
    <div className="space-y-6">
      <Field label="컬렉션 이름" htmlFor="c-title">
        <Input id="c-title" value={value.title} onChange={(e) => set({ title: e.target.value })} placeholder="예: 성수 카페" maxLength={30} />
      </Field>
      <Field label="설명" htmlFor="c-desc" optional>
        <Textarea id="c-desc" value={value.description} onChange={(e) => set({ description: e.target.value })} placeholder="어떤 장소를 모으는 컬렉션인가요?" maxLength={200} className="min-h-24" />
      </Field>
      <Field label="공개 범위" htmlFor="c-vis" hint="전체 공개 컬렉션은 다른 사람도 보고 장소를 저장할 수 있어요.">
        <SelectTrigger id="c-vis" value={visibilityLabel(value.visibility)} onClick={() => setVisibilityOpen(true)} />
      </Field>
      <Field label="지도 핀 색상">
        <div className="flex gap-2" role="radiogroup" aria-label="지도 핀 색상">
          {PIN_COLORS.map((c) => (
            <button
              key={c.value}
              type="button"
              role="radio"
              aria-checked={value.pinColor === c.value}
              onClick={() => set({ pinColor: c.value })}
              className={cn(
                "flex h-10 items-center gap-2 rounded-full border px-3 text-[13px]",
                value.pinColor === c.value ? "border-primary-deep/40 bg-selected font-bold text-primary-deep" : "border-line text-ink-sub",
              )}
            >
              <span className={cn("size-3.5 rounded-full", PIN_SWATCH[c.value])} />
              {c.label}
            </button>
          ))}
        </div>
      </Field>
      <VisibilitySheet open={visibilityOpen} value={value.visibility} onClose={() => setVisibilityOpen(false)} onChange={(v) => set({ visibility: v })} />
    </div>
  );
}
