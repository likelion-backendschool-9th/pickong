import type { Place } from "@/entities/place";

export interface DraftPhoto {
  key: string;
  url: string;
  place: Place | null;
}

/** "1 카페 온유 · 2,3 소품샵 모모 · 4 서울숲 · 5 없음" — 같은 장소의 사진 번호를 묶어 보여줍니다. */
export function summarizePhotoPlaces(photos: DraftPhoto[]): string {
  const groups: { label: string; numbers: number[] }[] = [];
  photos.forEach((p, i) => {
    const label = p.place?.name ?? "없음";
    const group = groups.find((g) => g.label === label);
    if (group) group.numbers.push(i + 1);
    else groups.push({ label, numbers: [i + 1] });
  });
  return groups.map((g) => `${g.numbers.join(",")} ${g.label}`).join(" · ");
}

export function uniqueDraftPlaces(photos: DraftPhoto[]): Place[] {
  const seen = new Map<string, Place>();
  for (const p of photos) if (p.place && !seen.has(p.place.id)) seen.set(p.place.id, p.place);
  return [...seen.values()];
}

export function movePhoto(photos: DraftPhoto[], from: number, to: number): DraftPhoto[] {
  if (to < 0 || to >= photos.length) return photos;
  const next = photos.slice();
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}
