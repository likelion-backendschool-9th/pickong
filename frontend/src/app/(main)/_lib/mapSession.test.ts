import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  hasBootstrappedMapGps,
  getMapSessionSnapshot,
  getServerMapSessionSnapshot,
  subscribeMapSession,
  markMapGpsBootstrapped,
  readMapCamera,
  readUserLocation,
  writeMapCamera,
  writeUserLocation,
} from "./mapSession";

beforeEach(() => {
  // Node 환경에도 브라우저와 같은 테스트 전용 저장소를 제공하며, 테스트마다 새로 만듭니다.
  const values = new Map<string, string>();
  const storage: Storage = {
    get length() { return values.size; },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key); },
    setItem: (key, value) => { values.set(key, value); },
  };
  vi.stubGlobal("sessionStorage", storage);
});

afterEach(() => vi.unstubAllGlobals());

describe("mapSession", () => {
  it("서버 스냅샷은 세션 내용과 관계없이 null이고 클라이언트 스냅샷은 값이 같으면 동일하다", () => {
    const before = getMapSessionSnapshot();
    expect(getMapSessionSnapshot()).toBe(before);
    writeMapCamera({ center: { lat: 37.5, lng: 127 }, zoom: 15 });
    expect(getMapSessionSnapshot()).not.toBe(before);
    expect(getMapSessionSnapshot()).toBe(getMapSessionSnapshot());
    expect(getServerMapSessionSnapshot()).toBeNull();
  });

  it("같은 탭의 카메라·위치·부트스트랩 쓰기를 구독하고 해제 후에는 알리지 않는다", () => {
    const changed = vi.fn();
    const unsubscribe = subscribeMapSession(changed);
    writeMapCamera({ center: { lat: 37.5, lng: 127 }, zoom: 15 });
    writeUserLocation({ lat: 37.56, lng: 126.98 });
    markMapGpsBootstrapped();
    expect(changed).toHaveBeenCalledTimes(3);
    unsubscribe();
    writeMapCamera({ center: { lat: 36, lng: 128 }, zoom: 12 });
    expect(changed).toHaveBeenCalledTimes(3);
    expect(JSON.parse(getMapSessionSnapshot()!)).toEqual({ bootstrapped: true, camera: { center: { lat: 36, lng: 128 }, zoom: 12 }, userLocation: { lat: 37.56, lng: 126.98 } });
  });

  it("구독이 중단된 동안 저장한 카메라도 복귀 시 최신 값으로 읽는다", () => {
    const unsubscribe = subscribeMapSession(vi.fn());
    unsubscribe();
    writeMapCamera({ center: { lat: 35, lng: 129 }, zoom: 13 });
    expect(JSON.parse(getMapSessionSnapshot()!).camera).toEqual({ center: { lat: 35, lng: 129 }, zoom: 13 });
  });

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
