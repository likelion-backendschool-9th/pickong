/** 카카오 지도 JS SDK에서 쓰는 최소 타입. `shared/map/kakao/` 밖에서 참조하지 않습니다. */

export {};

declare global {
  namespace kakao.maps {
    class LatLng {
      constructor(latitude: number, longitude: number);
      getLat(): number;
      getLng(): number;
    }

    class LatLngBounds {
      constructor(sw?: LatLng, ne?: LatLng);
      extend(latlng: LatLng): void;
      getSouthWest(): LatLng;
      getNorthEast(): LatLng;
    }

    interface MapOptions {
      center: LatLng;
      level: number;
    }

    class Map {
      constructor(container: HTMLElement, options: MapOptions);
      setCenter(latlng: LatLng): void;
      getCenter(): LatLng;
      setLevel(level: number, options?: { animate?: boolean }): void;
      getLevel(): number;
      setBounds(
        bounds: LatLngBounds,
        paddingTop?: number,
        paddingRight?: number,
        paddingBottom?: number,
        paddingLeft?: number,
      ): void;
      getBounds(): LatLngBounds;
      relayout(): void;
      panTo(latlng: LatLng): void;
    }

    interface CustomOverlayOptions {
      map?: Map | null;
      position: LatLng;
      content: HTMLElement | string;
      xAnchor?: number;
      yAnchor?: number;
      zIndex?: number;
      clickable?: boolean;
    }

    class CustomOverlay {
      constructor(options: CustomOverlayOptions);
      setMap(map: Map | null): void;
      setPosition(position: LatLng): void;
      setZIndex(zIndex: number): void;
    }

    interface PolylineOptions {
      map?: Map | null;
      path: LatLng[];
      strokeWeight?: number;
      strokeColor?: string;
      strokeOpacity?: number;
      strokeStyle?: string;
    }

    class Polyline {
      constructor(options: PolylineOptions);
      setMap(map: Map | null): void;
      setPath(path: LatLng[]): void;
    }

    function load(callback: () => void): void;

    namespace event {
      function addListener(target: object, type: string, handler: () => void): void;
      function removeListener(target: object, type: string, handler: () => void): void;
    }
  }

  interface Window {
    kakao: { maps: typeof kakao.maps };
  }

  // SDK 스크립트 로드 후 전역으로 존재
  var kakao: { maps: typeof kakao.maps };
}
