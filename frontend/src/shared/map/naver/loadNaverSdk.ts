let loadPromise: Promise<void> | null = null;

/**
 * 네이버 지도 JS SDK를 한 번만 로드합니다.
 * `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID`(NCP Maps Client ID / ncpKeyId)가 필요합니다.
 *
 * SDK는 URL `callback`을 `window.naver.maps` 할당보다 먼저 호출합니다.
 * 그래서 callback이 아니라 script.onload(스크립트 전체 실행 후)에서 준비 여부를 확인합니다.
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
    let settled = false;

    const fail = (message: string) => {
      if (settled) return;
      settled = true;
      loadPromise = null;
      reject(new Error(message));
    };

    const ok = () => {
      if (settled) return;
      settled = true;
      resolve();
    };

    const previousAuthFailure = window.navermap_authFailure;
    window.navermap_authFailure = () => {
      previousAuthFailure?.();
      fail("네이버 지도 인증에 실패했어요. Client ID와 서비스·도메인 설정을 확인해 주세요.");
    };

    const script = document.createElement("script");
    // callback 파라미터를 쓰지 않음: SDK가 maps 할당 전에 callback을 호출하기 때문
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}`;
    script.async = true;
    script.onload = () => {
      // onload는 스크립트 동기 실행이 끝난 뒤라 naver.maps가 붙어 있어야 함
      if (!window.naver?.maps) {
        fail("네이버 지도 SDK를 불러오지 못했어요.");
        return;
      }
      ok();
    };
    script.onerror = () => {
      fail("네이버 지도 SDK를 불러오지 못했어요.");
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
