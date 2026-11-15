import { describe, expect, it } from "vitest";
import { placeOfPhoto, type Post } from "./model";

const place = (id: string) =>
  ({ id, name: id, location: { lat: 0, lng: 0 } }) as unknown as Post["places"][number];

const post = {
  photos: [
    { url: "1", placeId: "onyu" },
    { url: "2", placeId: "momo" },
    { url: "3", placeId: "momo" },
    { url: "4", placeId: null },
  ],
  places: [place("onyu"), place("momo")],
} as unknown as Post;

describe("placeOfPhoto", () => {
  it("사진을 넘기면 그 사진에 연결된 장소를 돌려준다", () => {
    expect(placeOfPhoto(post, 0)?.id).toBe("onyu");
    expect(placeOfPhoto(post, 2)?.id).toBe("momo");
  });
  it("장소가 없는 사진은 null", () => {
    expect(placeOfPhoto(post, 3)).toBeNull();
  });
});
