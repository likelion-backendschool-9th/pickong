import { describe, expect, it } from "vitest";
import type { Place } from "@/entities/place";
import { movePhoto, summarizePhotoPlaces, uniqueDraftPlaces, type DraftPhoto } from "./draft";

const place = (id: string, name: string) => ({ id, name }) as Place;
const onyu = place("onyu", "카페 온유");
const momo = place("momo", "소품샵 모모");
const photos: DraftPhoto[] = [
  { key: "1", url: "", place: onyu },
  { key: "2", url: "", place: momo },
  { key: "3", url: "", place: momo },
  { key: "4", url: "", place: null },
];

describe("게시물 초안", () => {
  it("사진별 장소 연결 상태를 묶어서 요약한다", () => {
    expect(summarizePhotoPlaces(photos)).toBe("1 카페 온유 · 2,3 소품샵 모모 · 4 없음");
  });
  it("연결 장소는 중복 없이 센다", () => {
    expect(uniqueDraftPlaces(photos).map((p) => p.id)).toEqual(["onyu", "momo"]);
  });
  it("사진 순서를 바꾸면 연결된 장소도 함께 움직인다", () => {
    expect(movePhoto(photos, 3, 0).map((p) => p.place?.id ?? null)).toEqual([null, "onyu", "momo", "momo"]);
  });
});
