import { describe, expect, it } from "vitest";
import { geolocationErrorMessage, inBounds } from "./geo";

describe("inBounds", () => {
  const seongsu = { north: 37.55, south: 37.54, east: 127.06, west: 127.04 };

  it("범위 안의 좌표는 true", () => {
    expect(inBounds({ lat: 37.5446, lng: 127.0559 }, seongsu)).toBe(true);
  });

  it("경계선 위의 좌표도 포함", () => {
    expect(inBounds({ lat: 37.55, lng: 127.04 }, seongsu)).toBe(true);
  });

  it("범위 밖(다른 동네)은 false", () => {
    expect(inBounds({ lat: 37.5826, lng: 126.9836 }, seongsu)).toBe(false);
  });
});

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
