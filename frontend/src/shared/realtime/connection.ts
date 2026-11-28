import { Client, ReconnectionTimeMode, type StompSubscription } from "@stomp/stompjs";
import { ApiError } from "@/shared/api/errors";
import { resolveWsUrl } from "./wsUrl";

type Listener = (body: unknown) => void;

/**
 * 탭당 하나인 STOMP 연결 (frontend_blueprint 8.8, common_blueprint 5.9). 대화 같은 도메인은 모릅니다.
 * - 쓰는 곳이 하나라도 있으면(acquire) 연결하고, 모두 놓으면 잠시 뒤 끊습니다(화면 이동 중에 끊었다 다시 붙지 않게).
 * - heartbeat 10초, 끊기면 1초부터 두 배씩 최대 30초 간격으로 다시 연결합니다.
 * - 연결이 실패하거나 끊긴 뒤 다시 연결하기 전에는 확인 요청(probe)을 HTTP로 보냅니다. 브라우저는 핸드셰이크 실패의
 *   401 · 403을 알려 주지 않기 때문입니다. 401은 HTTP 클라이언트가 재발급하고, 재발급 실패 · 403(약관 재동의)이면
 *   기존 오류 처리(로그인 · 재동의 화면)로 넘어가므로 다시 연결하지 않습니다.
 * - 구독은 다시 연결되면 자동으로 다시 맺습니다.
 */
export interface RealtimeConnection {
  /** 연결을 씁니다. 돌려받은 함수로 놓습니다. */
  acquire(): () => void;
  /** 목적지를 구독합니다. 돌려받은 함수로 그만둡니다. 본문은 JSON으로 읽어 넘깁니다. */
  subscribe(destination: string, listener: Listener): () => void;
  /** (다시) 연결될 때마다 부릅니다. 끊긴 동안의 이벤트는 다시 오지 않으므로 HTTP로 보충할 때 씁니다. */
  onConnected(listener: () => void): () => void;
  /** 연결돼 있으면 보내고 true, 아니면 false */
  publish(destination: string, body: unknown): boolean;
  /** 연결될 때까지 기다립니다. 시간 안에 안 되면 실패합니다. */
  whenConnected(timeoutMs: number): Promise<void>;
  /** 연결 실패 뒤 다시 연결하기 전에 부를 확인 요청 */
  setProbe(probe: (() => Promise<unknown>) | null): void;
}

/** 화면 이동(목록 → 대화방)처럼 잠깐 쓰는 곳이 없어도 연결을 유지하는 시간 */
const RELEASE_GRACE_MS = 5_000;

/** 확인 요청이 이 오류면 다시 연결하지 않습니다 (재발급 실패 401 · 약관 재동의 등 403은 기존 오류 처리가 화면을 옮김). */
export function isFatalProbeError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}

export function createRealtimeConnection(url: () => string): RealtimeConnection {
  let client: Client | null = null;
  let refs = 0;
  let releaseTimer: ReturnType<typeof setTimeout> | null = null;
  /** 마지막 시도가 실패했거나 연결이 끊김 → 다음 시도 전에 확인 요청 */
  let needsProbe = false;
  let probe: (() => Promise<unknown>) | null = null;
  const listeners = new Map<string, Set<Listener>>();
  const remote = new Map<string, StompSubscription>();
  const connectedListeners = new Set<() => void>();
  let waiters: Array<() => void> = [];

  const subscribeRemote = (destination: string) => {
    if (!client?.connected || remote.has(destination)) return;
    remote.set(
      destination,
      client.subscribe(destination, (frame) => {
        let body: unknown;
        try {
          body = JSON.parse(frame.body);
        } catch {
          return;
        }
        listeners.get(destination)?.forEach((listener) => listener(body));
      }),
    );
  };

  const start = () => {
    const stomp = new Client({
      brokerURL: url(),
      reconnectDelay: 1_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 30_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async (c) => {
        if (!needsProbe || !probe) return;
        try {
          await probe();
        } catch (error) {
          if (isFatalProbeError(error)) await c.deactivate();
        }
      },
      onConnect: () => {
        needsProbe = false;
        remote.clear();
        for (const destination of listeners.keys()) subscribeRemote(destination);
        connectedListeners.forEach((listener) => listener());
        const ready = waiters;
        waiters = [];
        ready.forEach((resolve) => resolve());
      },
      onWebSocketClose: () => {
        needsProbe = true;
        remote.clear();
      },
      onStompError: () => {
        needsProbe = true;
      },
    });
    client = stomp;
    stomp.activate();
  };

  return {
    acquire() {
      refs += 1;
      if (releaseTimer) {
        clearTimeout(releaseTimer);
        releaseTimer = null;
      }
      if (!client) start();
      let released = false;
      return () => {
        if (released) return;
        released = true;
        refs -= 1;
        if (refs > 0) return;
        releaseTimer = setTimeout(() => {
          releaseTimer = null;
          if (refs > 0 || !client) return;
          void client.deactivate();
          client = null;
          remote.clear();
        }, RELEASE_GRACE_MS);
      };
    },

    subscribe(destination, listener) {
      const set = listeners.get(destination) ?? new Set<Listener>();
      set.add(listener);
      listeners.set(destination, set);
      subscribeRemote(destination);
      return () => {
        set.delete(listener);
        if (set.size > 0) return;
        listeners.delete(destination);
        remote.get(destination)?.unsubscribe();
        remote.delete(destination);
      };
    },

    onConnected(listener) {
      connectedListeners.add(listener);
      return () => connectedListeners.delete(listener);
    },

    publish(destination, body) {
      if (!client?.connected) return false;
      client.publish({ destination, body: JSON.stringify(body), headers: { "content-type": "application/json" } });
      return true;
    },

    whenConnected(timeoutMs) {
      if (client?.connected) return Promise.resolve();
      return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          waiters = waiters.filter((w) => w !== done);
          reject(new Error("STOMP 연결 시간 초과"));
        }, timeoutMs);
        const done = () => {
          clearTimeout(timer);
          resolve();
        };
        waiters.push(done);
      });
    },

    setProbe(next) {
      probe = next;
    },
  };
}

/** 앱 전체가 쓰는 연결 하나. 주소는 연결할 때(브라우저에서) 정합니다. */
export const realtime = createRealtimeConnection(() => resolveWsUrl(process.env.NEXT_PUBLIC_WS_URL, window.location));
