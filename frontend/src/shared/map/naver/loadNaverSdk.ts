import { shouldUseNaverGl } from "./config";

const SCRIPT_ATTR = "data-pickong-naver-sdk";
const AUTH_FAIL_MESSAGE = "네이버 지도 인증에 실패했어요. Client ID와 서비스·도메인 설정을 확인해 주세요.";
const LOAD_FAIL_MESSAGE = "네이버 지도 SDK를 불러오지 못했어요.";
const GL_FAIL_MESSAGE = "네이버 지도 GL 서브모듈을 불러오지 못했어요.";

let loadPromise: Promise<void> | null = null;
/** 인증 실패 후에는 전역 naver 객체가 남아 있어도 ready로 보지 않습니다. */
let authFailed = false;

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
        reject(new Error(GL_FAIL_MESSAGE));
        return;
      }
      window.setTimeout(tick, GL_READY_POLL_MS);
    };
    tick();
  });
}

function isSdkReady(): boolean {
  if (authFailed) return false;
  if (!window.naver?.maps) return false;
  if (!shouldUseNaverGl()) return true;
  return isNaverGlReady();
}

function removeManagedScripts() {
  for (const el of document.querySelectorAll(`script[${SCRIPT_ATTR}]`)) {
    el.remove();
  }
}

/**
 * 네이버 지도 JS SDK를 한 번만 로드합니다.
 * `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID`(NCP Maps Client ID / ncpKeyId)가 필요합니다.
 *
 * Style Editor 연동 시 GL 서브모듈이 필요합니다 (`submodules=gl`).
 * SDK는 URL `callback`을 `window.naver.maps` 할당보다 먼저 호출하므로,
 * callback이 아니라 script.onload에서 준비 여부를 확인합니다.
 * GL 사용 시에는 `maps-gl.js`까지 준비된 뒤에 resolve합니다.
 *
 * 실패 시 이번 시도의 script·authFailure 콜백을 정리해, 재시도 때 중복으로 남지 않게 합니다.
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

  // 인증 실패 이력이 있고 전역 SDK가 남아 있으면 스크립트를 또 붙이지 않고 안내만 합니다.
  // (이미 실행된 SDK는 script 제거로 되돌릴 수 없습니다. Client ID·도메인 수정 후 새로고침이 필요합니다.)
  if (authFailed && window.naver?.maps) {
    return Promise.reject(new Error(AUTH_FAIL_MESSAGE));
  }

  if (loadPromise) return loadPromise;

  loadPromise = new Promise<void>((resolve, reject) => {
    let settled = false;
    let script: HTMLScriptElement | null = null;
    const previousAuthFailure = window.navermap_authFailure;

    const restoreAuthFailure = () => {
      if (window.navermap_authFailure === onAuthFailure) {
        window.navermap_authFailure = previousAuthFailure;
      }
    };

    const cleanupAttempt = () => {
      if (script) {
        script.onload = null;
        script.onerror = null;
        script.remove();
        script = null;
      }
      restoreAuthFailure();
    };

    const fail = (message: string) => {
      if (settled) return;
      settled = true;
      cleanupAttempt();
      loadPromise = null;
      reject(new Error(message));
    };

    const ok = () => {
      if (settled) return;
      settled = true;
      authFailed = false;
      // 성공한 script는 유지. 로드 중 인증 콜백만 이전 값으로 되돌립니다.
      restoreAuthFailure();
      resolve();
    };

    const onAuthFailure = () => {
      authFailed = true;
      previousAuthFailure?.();
      fail(AUTH_FAIL_MESSAGE);
    };

    const finishAfterMaps = () => {
      if (authFailed) {
        fail(AUTH_FAIL_MESSAGE);
        return;
      }
      if (!window.naver?.maps) {
        fail(LOAD_FAIL_MESSAGE);
        return;
      }
      if (!shouldUseNaverGl()) {
        ok();
        return;
      }
      waitForNaverGlReady()
        .then(ok)
        .catch((err: unknown) => {
          fail(err instanceof Error ? err.message : GL_FAIL_MESSAGE);
        });
    };

    // 본체는 이미 있는데 GL만 늦게 붙는 중이면 스크립트를 다시 넣지 않고 대기합니다.
    if (window.naver?.maps) {
      finishAfterMaps();
      return;
    }

    // 이전 실패로 남은 managed script가 있으면 제거하고 새로 붙입니다.
    removeManagedScripts();

    window.navermap_authFailure = onAuthFailure;

    const params = new URLSearchParams({ ncpKeyId: clientId });
    if (shouldUseNaverGl()) params.set("submodules", "gl");

    script = document.createElement("script");
    script.setAttribute(SCRIPT_ATTR, "1");
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?${params.toString()}`;
    script.async = true;
    script.onload = () => finishAfterMaps();
    script.onerror = () => {
      fail(LOAD_FAIL_MESSAGE);
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
