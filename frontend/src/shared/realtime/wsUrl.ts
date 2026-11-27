import { API_BASE } from "@/shared/api/http";

/**
 * STOMP 연결 주소. NEXT_PUBLIC_WS_URL이 있으면 그 값(로컬: mock ws://localhost:4000/api/v1/ws, 백엔드 ws://localhost:8080/api/v1/ws),
 * 없으면 지금 페이지와 같은 도메인의 /api/v1/ws입니다(운영: 리버스 프록시가 백엔드로 전달).
 * Next rewrites는 WebSocket을 전달하지 않아서 로컬에서는 주소를 정해야 합니다 (common_blueprint 5.1).
 */
export function resolveWsUrl(configured: string | undefined, location: Pick<Location, "protocol" | "host">): string {
  if (configured && configured.trim()) return configured.trim();
  const scheme = location.protocol === "https:" ? "wss:" : "ws:";
  return `${scheme}//${location.host}${API_BASE}/ws`;
}
