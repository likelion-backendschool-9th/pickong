import { afterEach, describe, expect, it, vi } from "vitest";
import { geolocationErrorMessage, getCurrentPosition, inBounds } from "./geo";

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

describe("getCurrentPosition", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubGeolocation(
    impl: (
      success: PositionCallback,
      error?: PositionErrorCallback,
      options?: PositionOptions,
    ) => void,
  ) {
    vi.stubGlobal("navigator", {
      geolocation: {
        getCurrentPosition: impl,
      },
    });
  }

  it("성공 시 위도·경도를 반환한다", async () => {
    stubGeolocation((success) => {
      success({
        coords: {
          latitude: 37.5446,
          longitude: 127.0559,
          accuracy: 10,
          altitude: null,
          altitudeAccuracy: null,
          heading: null,
          speed: null,
          toJSON() {
            return this;
          },
        },
        timestamp: Date.now(),
        toJSON() {
          return this;
        },
      });
    });

    await expect(getCurrentPosition()).resolves.toEqual({ lat: 37.5446, lng: 127.0559 });
  });

  it("위치 권한 거부(code 1)를 사용자 메시지로 거절한다", async () => {
    stubGeolocation((_success, error) => {
      error?.({
        code: 1,
        message: "User denied Geolocation",
        PERMISSION_DENIED: 1,
        POSITION_UNAVAILABLE: 2,
        TIMEOUT: 3,
      });
    });

    await expect(getCurrentPosition()).rejects.toThrow(/권한/);
  });

  it("위치를 찾을 수 없음(code 2)을 사용자 메시지로 거절한다", async () => {
    stubGeolocation((_success, error) => {
      error?.({
        code: 2,
        message: "Position unavailable",
        PERMISSION_DENIED: 1,
        POSITION_UNAVAILABLE: 2,
        TIMEOUT: 3,
      });
    });

    await expect(getCurrentPosition()).rejects.toThrow(/확인할 수 없/);
  });

  it("타임아웃(code 3)을 사용자 메시지로 거절한다", async () => {
    stubGeolocation((_success, error) => {
      error?.({
        code: 3,
        message: "Timeout expired",
        PERMISSION_DENIED: 1,
        POSITION_UNAVAILABLE: 2,
        TIMEOUT: 3,
      });
    });

    await expect(getCurrentPosition()).rejects.toThrow(/시간/);
  });

  it("geolocation이 없으면 거절한다", async () => {
    vi.stubGlobal("navigator", {});
    await expect(getCurrentPosition()).rejects.toThrow(/위치를 사용할 수 없/);
  });
});
