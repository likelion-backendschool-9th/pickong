let loadPromise: Promise<void> | null = null;

/**
 * 카카오 지도 JS SDK를 한 번만 로드합니다.
 * `NEXT_PUBLIC_KAKAO_MAP_KEY`가 필요합니다.
 */
export function loadKakaoSdk(): Promise<void> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("카카오 지도 SDK는 브라우저에서만 로드할 수 있어요."));
  }

  const key = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;
  if (!key) {
    return Promise.reject(new Error("NEXT_PUBLIC_KAKAO_MAP_KEY가 설정되지 않았어요."));
  }

  if (window.kakao?.maps) {
    return new Promise((resolve) => {
      window.kakao.maps.load(() => resolve());
    });
  }

  if (loadPromise) return loadPromise;

  loadPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.onload = () => {
      if (!window.kakao?.maps) {
        loadPromise = null;
        reject(new Error("카카오 지도 SDK를 불러오지 못했어요."));
        return;
      }
      window.kakao.maps.load(() => resolve());
    };
    script.onerror = () => {
      loadPromise = null;
      reject(new Error("카카오 지도 SDK를 불러오지 못했어요."));
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
