import type { PlaceCategory } from "@/entities/place";

export interface ChipItem {
  category: PlaceCategory;
  visible: boolean;
}

/** 편집 목록: 보이는 칩을 저장된 순서대로, 그 뒤에 숨긴 칩을 기본 순서대로 */
export function buildChipItems(visible: PlaceCategory[], all: PlaceCategory[]): ChipItem[] {
  return [
    ...visible.filter((c) => all.includes(c)).map((category) => ({ category, visible: true })),
    ...all.filter((c) => !visible.includes(c)).map((category) => ({ category, visible: false })),
  ];
}

/** index번째 칩을 위(-1) · 아래(+1)로 한 칸 옮깁니다. 끝에서는 그대로 */
export function moveChip(items: ChipItem[], index: number, dir: -1 | 1): ChipItem[] {
  const to = index + dir;
  if (to < 0 || to >= items.length) return items;
  const next = [...items];
  [next[index], next[to]] = [next[to], next[index]];
  return next;
}

export function toggleChip(items: ChipItem[], index: number): ChipItem[] {
  return items.map((it, i) => (i === index ? { ...it, visible: !it.visible } : it));
}

/** 저장할 값: 보이는 칩만 순서대로 */
export const visibleCategories = (items: ChipItem[]) => items.filter((it) => it.visible).map((it) => it.category);
