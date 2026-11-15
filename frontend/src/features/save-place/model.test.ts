import { describe, expect, it } from "vitest";
import { describeSaveResult, uniquePlaceIds } from "./model";

describe("uniquePlaceIds", () => {
  it("사진 2·3에 같은 장소가 있어도 한 번만 남기고, 장소 없는 사진은 제외한다", () => {
    expect(uniquePlaceIds(["onyu", "momo", "momo", "forest", null])).toEqual(["onyu", "momo", "forest"]);
  });
});

describe("describeSaveResult", () => {
  it("새로 저장한 장소와 기존 장소 연결을 구분해서 알려준다", () => {
    expect(describeSaveResult({ createdPlaceIds: ["momo", "forest"], linkedPlaceIds: ["onyu"] })).toEqual({
      message: "장소 3곳 정리 완료",
      detail: "새로 저장 2곳 · 기존 장소 1곳 연결",
    });
  });
  it("이미 저장된 장소 하나는 컬렉션 연결로 안내한다", () => {
    expect(describeSaveResult({ createdPlaceIds: [], linkedPlaceIds: ["onyu"] }).message).toBe("컬렉션에 연결했어요");
  });
});
