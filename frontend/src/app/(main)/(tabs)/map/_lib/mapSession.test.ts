import { afterEach, describe, expect, it } from "vitest";
import {
  hasBootstrappedMapGps,
  markMapGpsBootstrapped,
  readMapCamera,
  readUserLocation,
  writeMapCamera,
  writeUserLocation,
} from "./mapSession";

afterEach(() => {
  sessionStorage.clear();
});

describe("mapSession", () => {
  it("GPS 부트스트랩 플래그를 세션에 기록한다", () => {
    expect(hasBootstrappedMapGps()).toBe(false);
    markMapGpsBootstrapped();
    expect(hasBootstrappedMapGps()).toBe(true);
  });

  it("카메라 중심·줌을 읽고 쓴다", () => {
    expect(readMapCamera()).toBeNull();
    writeMapCamera({ center: { lat: 37.5, lng: 127.0 }, zoom: 15.5 });
    expect(readMapCamera()).toEqual({ center: { lat: 37.5, lng: 127.0 }, zoom: 15.5 });
  });

  it("깨진 카메라 JSON은 null로 무시한다", () => {
    sessionStorage.setItem("pickong:map-camera", "{not-json");
    expect(readMapCamera()).toBeNull();
  });

  it("내 위치 좌표를 읽고 쓴다", () => {
    expect(readUserLocation()).toBeNull();
    writeUserLocation({ lat: 37.56, lng: 126.98 });
    expect(readUserLocation()).toEqual({ lat: 37.56, lng: 126.98 });
  });
});
