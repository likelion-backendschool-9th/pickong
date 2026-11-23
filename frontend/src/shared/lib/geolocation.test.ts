import { describe, expect, it } from "vitest";
import { geolocationErrorMessage } from "./geolocation";

describe("geolocationErrorMessage", () => {
  it("권한 거절", () => {
    expect(geolocationErrorMessage(1)).toContain("권한");
  });

  it("위치 불가", () => {
    expect(geolocationErrorMessage(2)).toContain("확인할 수 없");
  });

  it("시간 초과", () => {
    expect(geolocationErrorMessage(3)).toContain("시간");
  });

  it("알 수 없는 코드", () => {
    expect(geolocationErrorMessage(99)).toBe("위치를 불러오지 못했어요.");
  });
});
