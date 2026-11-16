"use client";

import { ChevronDown, ChevronUp } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { ALL_CATEGORIES, CATEGORY_LABEL, FILTER_CATEGORIES, useSaveMapCategories, type PlaceCategory } from "@/entities/place";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { CheckMark } from "@/shared/ui/controls";
import { useToast } from "@/shared/ui/Toast";
import { buildChipItems, moveChip, toggleChip, visibleCategories, type ChipItem } from "../_lib/categoryChips";

/** 내 지도 카테고리 칩 편집: 보이기/숨기기와 순서 바꾸기. 설정은 계정에 저장됩니다. */
export function CategoryChipEditor({
  open,
  onClose,
  current,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  current: PlaceCategory[];
  onSaved: (categories: PlaceCategory[]) => void;
}) {
  return (
    <BottomSheet open={open} onClose={onClose} title="카테고리 칩 편집" description="보일 칩을 고르고 순서를 바꿔요.">
      {open && <EditorBody current={current} onClose={onClose} onSaved={onSaved} />}
    </BottomSheet>
  );
}

function EditorBody({ current, onClose, onSaved }: { current: PlaceCategory[]; onClose: () => void; onSaved: (c: PlaceCategory[]) => void }) {
  const [items, setItems] = useState<ChipItem[]>(() => buildChipItems(current, ALL_CATEGORIES));
  const save = useSaveMapCategories();
  const toast = useToast();

  const submit = () =>
    save.mutate(visibleCategories(items), {
      onSuccess: (categories) => {
        onSaved(categories);
        onClose();
      },
      onError: (err) => toast({ message: errorMessage(err) }),
    });

  return (
    <>
      <ul className="divide-y divide-line">
        {items.map((it, i) => {
          const label = CATEGORY_LABEL[it.category];
          return (
            <li key={it.category} className="flex items-center gap-2 py-1">
              <button
                type="button"
                role="checkbox"
                aria-checked={it.visible}
                onClick={() => setItems((prev) => toggleChip(prev, i))}
                className="flex min-h-11 flex-1 items-center gap-3 text-left"
              >
                <CheckMark checked={it.visible} />
                <span className={it.visible ? "text-[15px] font-bold" : "text-[15px] text-ink-faint"}>{label}</span>
              </button>
              <button
                type="button"
                aria-label={`${label} 위로`}
                disabled={i === 0}
                onClick={() => setItems((prev) => moveChip(prev, i, -1))}
                className="inline-flex size-10 items-center justify-center rounded-full text-ink-sub active:bg-fill disabled:opacity-30"
              >
                <ChevronUp size={20} />
              </button>
              <button
                type="button"
                aria-label={`${label} 아래로`}
                disabled={i === items.length - 1}
                onClick={() => setItems((prev) => moveChip(prev, i, 1))}
                className="inline-flex size-10 items-center justify-center rounded-full text-ink-sub active:bg-fill disabled:opacity-30"
              >
                <ChevronDown size={20} />
              </button>
            </li>
          );
        })}
      </ul>
      <p className="mt-3 text-[12px] leading-relaxed text-ink-faint">
        카테고리는 장소의 종류라 이름은 바꿀 수 없어요. 나만의 분류가 필요하면{" "}
        <Link href={routes.newCollection()} className="font-bold text-primary-deep underline-offset-2 hover:underline">
          컬렉션
        </Link>
        을 만들어 보세요. 컬렉션은 위쪽 줄에서 바로 거를 수 있어요.
      </p>
      <div className="mt-4 grid grid-cols-[auto_1fr] gap-2">
        <Button variant="ghost" size="md" onClick={() => setItems(buildChipItems(FILTER_CATEGORIES, ALL_CATEGORIES))}>
          기본값으로
        </Button>
        <Button size="md" onClick={submit} disabled={save.isPending}>
          {save.isPending ? "저장 중…" : "저장"}
        </Button>
      </div>
    </>
  );
}
