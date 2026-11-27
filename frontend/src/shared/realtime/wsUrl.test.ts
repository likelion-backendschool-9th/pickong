import { describe, expect, it } from "vitest";
import { resolveWsUrl } from "./wsUrl";

describe("resolveWsUrl", () => {
  it("정한 주소가 있으면 그대로", () => {
    expect(resolveWsUrl(" ws://localhost:8080/api/v1/ws ", { protocol: "http:", host: "localhost:3000" })).toBe("ws://localhost:8080/api/v1/ws");
  });

  it("없으면 같은 도메인의 /api/v1/ws (https면 wss)", () => {
    expect(resolveWsUrl(undefined, { protocol: "https:", host: "pickong.app" })).toBe("wss://pickong.app/api/v1/ws");
    expect(resolveWsUrl("", { protocol: "http:", host: "localhost:3000" })).toBe("ws://localhost:3000/api/v1/ws");
  });
});
