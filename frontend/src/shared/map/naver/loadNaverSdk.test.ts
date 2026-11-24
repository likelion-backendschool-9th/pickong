import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type ScriptEl = {
  src: string;
  async: boolean;
  onload: ((ev?: unknown) => void) | null;
  onerror: ((ev?: unknown) => void) | null;
  setAttribute: (name: string, value: string) => void;
  remove: () => void;
  getAttribute: (name: string) => string | null;
  attrs: Record<string, string>;
};

function installDomMock(options?: { failMode?: "network" | "auth" | "success" }) {
  const failMode = options?.failMode ?? "network";
  const scripts: ScriptEl[] = [];
  const head = {
    appendChild(el: ScriptEl) {
      scripts.push(el);
      queueMicrotask(() => {
        if (failMode === "network") {
          el.onerror?.(new Event("error"));
          return;
        }
        if (failMode === "auth") {
          (globalThis as { navermap_authFailure?: () => void }).navermap_authFailure?.();
          return;
        }
        (globalThis as { naver?: { maps: object } }).naver = { maps: { Map: class Map {} } };
        el.onload?.(new Event("load"));
      });
      return el;
    },
  };

  const doc = {
    head,
    createElement(tag: string) {
      if (tag !== "script") throw new Error(`unexpected tag ${tag}`);
      const el: ScriptEl = {
        src: "",
        async: false,
        onload: null,
        onerror: null,
        attrs: {},
        setAttribute(name, value) {
          this.attrs[name] = value;
        },
        getAttribute(name) {
          return this.attrs[name] ?? null;
        },
        remove() {
          const i = scripts.indexOf(this);
          if (i >= 0) scripts.splice(i, 1);
        },
      };
      return el;
    },
    querySelectorAll(selector: string) {
      if (selector === 'script[data-pickong-naver-sdk]') {
        return scripts.filter((s) => s.attrs["data-pickong-naver-sdk"] === "1") as unknown as NodeListOf<Element>;
      }
      return [] as unknown as NodeListOf<Element>;
    },
  };

  vi.stubGlobal("window", globalThis);
  vi.stubGlobal("document", doc);
  (globalThis as { document: typeof doc }).document = doc;
  (globalThis as { navermap_authFailure?: () => void }).navermap_authFailure = undefined;
  delete (globalThis as { naver?: unknown }).naver;

  return {
    scripts,
    managedCount: () => scripts.filter((s) => s.attrs["data-pickong-naver-sdk"] === "1").length,
  };
}

describe("loadNaverSdk", () => {
  beforeEach(() => {
    vi.resetModules();
    process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID = "test-client-id";
    delete process.env.NEXT_PUBLIC_NAVER_MAP_STYLE_ID;
    delete process.env.NEXT_PUBLIC_NAVER_MAP_GL;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (globalThis as { navermap_authFailure?: unknown }).navermap_authFailure;
    delete (globalThis as { naver?: unknown }).naver;
  });

  it("Client ID가 없으면 스크립트를 붙이지 않고 거절한다", async () => {
    delete process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID;
    const dom = installDomMock();
    const { loadNaverSdk } = await import("./loadNaverSdk");
    await expect(loadNaverSdk()).rejects.toThrow(/NEXT_PUBLIC_NAVER_MAP_CLIENT_ID/);
    expect(dom.managedCount()).toBe(0);
  });

  it("네트워크 실패 후 재시도해도 managed 스크립트와 auth 콜백이 쌓이지 않는다", async () => {
    const dom = installDomMock({ failMode: "network" });
    const prior = vi.fn();
    (globalThis as { navermap_authFailure?: () => void }).navermap_authFailure = prior;

    const { loadNaverSdk } = await import("./loadNaverSdk");

    for (let i = 0; i < 3; i++) {
      await expect(loadNaverSdk()).rejects.toThrow(/불러오지 못했어요/);
      expect(dom.managedCount()).toBe(0);
      expect((globalThis as { navermap_authFailure?: () => void }).navermap_authFailure).toBe(prior);
    }
  });

  it("인증 실패 시 안내 메시지를 주고 콜백·스크립트를 정리한다", async () => {
    const dom = installDomMock({ failMode: "auth" });
    const prior = vi.fn();
    (globalThis as { navermap_authFailure?: () => void }).navermap_authFailure = prior;

    const { loadNaverSdk } = await import("./loadNaverSdk");
    await expect(loadNaverSdk()).rejects.toThrow(/인증에 실패/);
    expect(prior).toHaveBeenCalledTimes(1);
    expect(dom.managedCount()).toBe(0);
    expect((globalThis as { navermap_authFailure?: () => void }).navermap_authFailure).toBe(prior);
  });

  it("인증 실패 후 재호출 시 스크립트를 추가로 붙이지 않는다", async () => {
    const dom = installDomMock({ failMode: "auth" });
    const { loadNaverSdk } = await import("./loadNaverSdk");

    await expect(loadNaverSdk()).rejects.toThrow(/인증에 실패/);
    (globalThis as { naver?: { maps: object } }).naver = { maps: {} };

    const before = dom.scripts.length;
    await expect(loadNaverSdk()).rejects.toThrow(/인증에 실패/);
    expect(dom.scripts.length).toBe(before);
    expect(dom.managedCount()).toBe(0);
  });
});
