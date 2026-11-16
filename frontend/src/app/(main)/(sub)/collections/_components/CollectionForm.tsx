"use client";

import { useState } from "react";
import { PIN_COLORS, type PinColor } from "@/entities/collection";
import { useMe } from "@/entities/user";
import type { Visibility } from "@/shared/config/visibility";
import { visibilityLabel } from "@/shared/config/visibility";
import { cn } from "@/shared/lib/cn";
import { formatWon } from "@/shared/lib/money";
import { Switch } from "@/shared/ui/controls";
import { Field, Input, SelectTrigger, Textarea } from "@/shared/ui/form";
import { VisibilitySheet } from "@/shared/ui/VisibilitySheet";

export interface CollectionFormValue {
  title: string;
  description: string;
  visibility: Visibility;
  pinColor: PinColor;
  /** 유료 판매 가격(원). null이면 무료 */
  price: number | null;
}

export const PRICE_MIN = 500;
export const PRICE_MAX = 50000;
const DEFAULT_PRICE = 1900;

/** 가격 확인: 500~50,000원, 100원 단위. 문제가 없으면 null */
export function priceError(price: number | null): string | null {
  if (price === null) return null;
  if (!Number.isInteger(price) || price < PRICE_MIN || price > PRICE_MAX) return `${formatWon(PRICE_MIN)} ~ ${formatWon(PRICE_MAX)} 사이로 정해 주세요`;
  if (price % 100 !== 0) return "100원 단위로 정해 주세요";
  return null;
}

const PIN_SWATCH: Record<PinColor, string> = {
  LIME: "bg-primary",
  GREEN: "bg-primary-deep",
  BROWN: "bg-[#8a6a4f]",
};

/** 컬렉션 만들기·편집 공용 폼 */
export function CollectionForm({ value, onChange }: { value: CollectionFormValue; onChange: (v: CollectionFormValue) => void }) {
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  const { data: me } = useMe();
  const set = (patch: Partial<CollectionFormValue>) => onChange({ ...value, ...patch });
  const paid = value.price !== null;
  const error = priceError(value.price);

  return (
    <div className="space-y-6">
      <Field label="컬렉션 이름" htmlFor="c-title">
        <Input id="c-title" value={value.title} onChange={(e) => set({ title: e.target.value })} placeholder="예: 성수 카페" maxLength={30} />
      </Field>
      <Field label="설명" htmlFor="c-desc" optional>
        <Textarea id="c-desc" value={value.description} onChange={(e) => set({ description: e.target.value })} placeholder="어떤 장소를 모으는 컬렉션인가요?" maxLength={200} className="min-h-24" />
      </Field>
      <Field label="공개 범위" htmlFor="c-vis" hint={paid ? "유료 컬렉션은 전체 공개로만 판매돼요." : "전체 공개 컬렉션은 다른 사람도 보고 장소를 저장할 수 있어요."}>
        <SelectTrigger id="c-vis" value={visibilityLabel(value.visibility)} onClick={() => setVisibilityOpen(true)} />
      </Field>

      {/* 크리에이터만: 유료 판매 (단건 구매, 미리보기 2곳 공개) */}
      {me?.isCreator && (
        <div className="rounded-card border border-line px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <span className="min-w-0">
              <span className="block text-[15px] font-bold">유료로 판매</span>
              <span className="block text-[12px] text-ink-sub">앞의 2곳은 미리보기로 보이고, 나머지는 구매한 사람만 봐요</span>
            </span>
            <Switch
              label="유료로 판매"
              hideLabel
              checked={paid}
              onChange={(on) => set(on ? { price: DEFAULT_PRICE, visibility: "PUBLIC" } : { price: null })}
              className="shrink-0"
            />
          </div>
          {paid && (
            <Field label="가격" htmlFor="c-price" className="mt-3" error={error ?? undefined} hint="한 번 구매하면 계속 볼 수 있어요 · 지금은 테스트 결제만 연결돼 있어요">
              <div className="relative">
                <Input
                  id="c-price"
                  type="number"
                  inputMode="numeric"
                  min={PRICE_MIN}
                  max={PRICE_MAX}
                  step={100}
                  value={value.price ?? ""}
                  onChange={(e) => set({ price: e.target.value === "" ? 0 : Number(e.target.value) })}
                  className="pr-10 tabular-nums"
                />
                <span className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-[14px] text-ink-sub">원</span>
              </div>
            </Field>
          )}
        </div>
      )}
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
      <VisibilitySheet
        open={visibilityOpen}
        value={value.visibility}
        onClose={() => setVisibilityOpen(false)}
        // 전체 공개가 아니면 유료 판매를 끕니다.
        onChange={(v) => set(v === "PUBLIC" ? { visibility: v } : { visibility: v, price: null })}
      />
    </div>
  );
}
