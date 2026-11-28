import { describe, expect, it } from "vitest";
import { adminPlaceFileKey, reusedAdminPlaceImage } from "./uploadCache";

const file = { name: "a.jpg", size: 12, lastModified: 10 };

describe("관리자 장소 사진 업로드 재사용", () => {
  it("같은 파일은 기존 주소를 다시 쓰고 다른 파일은 다시 올립니다", () => {
    const cache = { key: adminPlaceFileKey(file), imageUrl: "/api/v1/admin/places/mock-media/a" };
    expect(reusedAdminPlaceImage(cache, file)).toBe(cache.imageUrl);
    expect(reusedAdminPlaceImage(null, file)).toBeNull();
    expect(reusedAdminPlaceImage(cache, { ...file, name: "b.jpg" })).toBeNull();
    expect(reusedAdminPlaceImage(cache, { ...file, size: 13 })).toBeNull();
    expect(reusedAdminPlaceImage(cache, { ...file, lastModified: 11 })).toBeNull();
  });
});
