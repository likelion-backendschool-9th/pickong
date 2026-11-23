let loadPromise: Promise<void> | null = null;

/**
 * 네이버 지도 JS SDK를 한 번만 로드합니다.
 * `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID`(NCP Maps Client ID / ncpKeyId)가 필요합니다.
 */
export function loadNaverSdk(): Promise<void> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("네이버 지도 SDK는 브라우저에서만 로드할 수 있어요."));
  }

  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID;
  if (!clientId) {
    return Promise.reject(new Error("NEXT_PUBLIC_NAVER_MAP_CLIENT_ID가 설정되지 않았어요."));
  }

  if (window.naver?.maps) {
    return Promise.resolve();
  }

  if (loadPromise) return loadPromise;

  loadPromise = new Promise<void>((resolve, reject) => {
    const fail = (message: string) => {
      loadPromise = null;
      reject(new Error(message));
    };

    const previousAuthFailure = window.navermap_authFailure;
    window.navermap_authFailure = () => {
      previousAuthFailure?.();
      fail("네이버 지도 인증에 실패했어요. Client ID와 서비스·도메인 설정을 확인해 주세요.");
    };

    const callbackName = `__pickongNaverMapsInit_${Date.now()}`;
    (window as unknown as Record<string, () => void>)[callbackName] = () => {
      delete (window as unknown as Record<string, unknown>)[callbackName];
      if (!window.naver?.maps) {
        fail("네이버 지도 SDK를 불러오지 못했어요.");
        return;
      }
      resolve();
    };

    const script = document.createElement("script");
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}&callback=${callbackName}`;
    script.async = true;
    script.onerror = () => {
      delete (window as unknown as Record<string, unknown>)[callbackName];
      fail("네이버 지도 SDK를 불러오지 못했어요.");
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
