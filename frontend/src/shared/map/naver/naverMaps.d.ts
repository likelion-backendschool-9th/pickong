/** 네이버 지도 JS SDK에서 쓰는 최소 타입. `shared/map/naver/` 밖에서 참조하지 않습니다. */

export {};

declare global {
  namespace naver.maps {
    class LatLng {
      constructor(lat: number, lng: number);
      lat(): number;
      lng(): number;
    }

    class LatLngBounds {
      constructor(sw?: LatLng, ne?: LatLng);
      extend(latlng: LatLng): LatLngBounds;
      getSW(): LatLng;
      getNE(): LatLng;
    }

    class Point {
      constructor(x: number, y: number);
    }

    class Size {
      constructor(width: number, height: number);
    }

    interface MapOptions {
      center?: LatLng;
      zoom?: number;
      minZoom?: number;
      maxZoom?: number;
      scaleControl?: boolean;
      mapDataControl?: boolean;
      logoControl?: boolean;
      zoomControl?: boolean;
      /** GL 벡터맵 (Style Editor 연동 시 필수) */
      gl?: boolean;
      /** Style Editor에서 발행한 My Style ID (GL 전용) */
      customStyleId?: string;
    }

    class Map {
      constructor(container: string | HTMLElement, options?: MapOptions);
      setCenter(latlng: LatLng): void;
      getCenter(): LatLng;
      setZoom(zoom: number, useTransition?: boolean): void;
      getZoom(): number;
      /** GL 지도는 margins에 숫자를 주면 오류가 나므로 객체만 허용합니다. */
      fitBounds(bounds: LatLngBounds, margins?: Partial<Record<"top" | "right" | "bottom" | "left", number>>): void;
      getBounds(): LatLngBounds;
      panTo(latlng: LatLng, transitionOptions?: object): void;
      /** 중심·줌을 한 번에 애니메이션으로 맞춥니다. */
      morph(latlng: LatLng, zoom?: number, transitionOptions?: object): void;
      autoResize(): void;
      destroy(): void;
    }

    interface MarkerIcon {
      content?: string | HTMLElement;
      size?: Size;
      anchor?: Point;
    }

    interface MarkerOptions {
      map?: Map | null;
      position: LatLng;
      icon?: MarkerIcon;
      title?: string;
      zIndex?: number;
      clickable?: boolean;
    }

    class Marker {
      constructor(options: MarkerOptions);
      setMap(map: Map | null): void;
      setPosition(position: LatLng): void;
      setIcon(icon: MarkerIcon): void;
      setZIndex(zIndex: number): void;
      getElement(): HTMLElement;
    }

    interface PolylineOptions {
      map?: Map | null;
      path: LatLng[];
      strokeWeight?: number;
      strokeColor?: string;
      strokeOpacity?: number;
      strokeStyle?: string;
      zIndex?: number;
    }

    class Polyline {
      constructor(options: PolylineOptions);
      setMap(map: Map | null): void;
      setPath(path: LatLng[]): void;
    }

    namespace Event {
      function addListener(target: object, eventName: string, listener: (...args: never[]) => void): object;
      function removeListener(listener: object): void;
    }
  }

  interface Window {
    naver?: { maps: typeof naver.maps };
    navermap_authFailure?: () => void;
  }

  var naver: { maps: typeof naver.maps };
}
