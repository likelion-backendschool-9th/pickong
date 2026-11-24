import { shouldUseNaverGl } from "./config";

let loadPromise: Promise<void> | null = null;

const GL_READY_TIMEOUT_MS = 15_000;
const GL_READY_POLL_MS = 40;

/**
 * `maps.js?submodules=gl` 은 본체 onload 이후에 `maps-gl.js`를 비동기로 붙입니다.
 * 본체만 준비된 상태에서 `gl: true` 지도를 만들면 래스터(basic)로 떨어져 커스텀 스타일이 무시됩니다.
 */
function isNaverGlReady(): boolean {
  const maps = window.naver?.maps as
    | (typeof naver.maps & {
        glEnabled?: boolean;
        WebGLHelper?: unknown;
        VectorMapType?: unknown;
      })
    | undefined;
  if (!maps) return false;
  return maps.glEnabled === true || typeof maps.WebGLHelper === "function" || typeof maps.VectorMapType === "function";
}

function waitForNaverGlReady(): Promise<void> {
  if (isNaverGlReady()) return Promise.resolve();

  return new Promise<void>((resolve, reject) => {
    const started = Date.now();
    const tick = () => {
      if (isNaverGlReady()) {
        resolve();
        return;
      }
      if (Date.now() - started > GL_READY_TIMEOUT_MS) {
        reject(new Error("네이버 지도 GL 서브모듈을 불러오지 못했어요."));
        return;
      }
      window.setTimeout(tick, GL_READY_POLL_MS);
    };
    tick();
  });
}

function isSdkReady(): boolean {
  if (!window.naver?.maps) return false;
  if (!shouldUseNaverGl()) return true;
  return isNaverGlReady();
}

/**
 * 네이버 지도 JS SDK를 한 번만 로드합니다.
 * `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID`(NCP Maps Client ID / ncpKeyId)가 필요합니다.
 *
 * Style Editor 연동 시 GL 서브모듈이 필요합니다 (`submodules=gl`).
 * SDK는 URL `callback`을 `window.naver.maps` 할당보다 먼저 호출하므로,
 * callback이 아니라 script.onload에서 준비 여부를 확인합니다.
 * GL 사용 시에는 `maps-gl.js`까지 준비된 뒤에 resolve합니다.
 */
export function loadNaverSdk(): Promise<void> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("네이버 지도 SDK는 브라우저에서만 로드할 수 있어요."));
  }

  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID;
  if (!clientId) {
    return Promise.reject(new Error("NEXT_PUBLIC_NAVER_MAP_CLIENT_ID가 설정되지 않았어요."));
  }

  if (isSdkReady()) {
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

    const finishAfterMaps = () => {
      if (!window.naver?.maps) {
        fail("네이버 지도 SDK를 불러오지 못했어요.");
        return;
      }
      if (!shouldUseNaverGl()) {
        ok();
        return;
      }
      waitForNaverGlReady().then(ok).catch((err: unknown) => {
        fail(err instanceof Error ? err.message : "네이버 지도 GL 서브모듈을 불러오지 못했어요.");
      });
    };

    // 본체는 이미 있는데 GL만 늦게 붙는 중이면 스크립트를 다시 넣지 않고 대기합니다.
    if (window.naver?.maps) {
      finishAfterMaps();
      return;
    }

    const previousAuthFailure = window.navermap_authFailure;
    window.navermap_authFailure = () => {
      previousAuthFailure?.();
      fail("네이버 지도 인증에 실패했어요. Client ID와 서비스·도메인 설정을 확인해 주세요.");
    };

    const params = new URLSearchParams({ ncpKeyId: clientId });
    if (shouldUseNaverGl()) params.set("submodules", "gl");

    const script = document.createElement("script");
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?${params.toString()}`;
    script.async = true;
    script.onload = () => finishAfterMaps();
    script.onerror = () => {
      fail("네이버 지도 SDK를 불러오지 못했어요.");
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
