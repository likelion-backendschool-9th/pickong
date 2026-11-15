import { db, distanceMeters, findPlace, ME_ID, nextId, planDto } from "../db.ts";
import type { PlanItemRow, PlanRow } from "../fixtures/types.ts";
import { HttpError, notFound, sleep, type Route } from "../router.ts";

const planOr404 = (id: string) => {
  const plan = db.plans.find((p) => p.id === id && p.ownerId === ME_ID);
  return plan ?? notFound("일정");
};

function parseItems(raw: unknown, previous: PlanItemRow[] = []): PlanItemRow[] {
  if (!Array.isArray(raw)) throw new HttpError(400, "BAD_ITEMS", "일정 항목 형식이 올바르지 않아요.");
  return raw.map((it) => {
    if (!findPlace(it.placeId)) throw new HttpError(400, "BAD_PLACE", "장소를 찾을 수 없어요.");
    const before = previous.find((p) => p.id === it.id);
    // 확정 예약 시간은 일정 편집으로 바뀌지 않도록 서버에서도 기존 값을 유지합니다.
    const reservation =
      it.kind === "RESERVATION" ? (before?.reservation && !it.reservationEdited ? before.reservation : it.reservation) : null;
    return {
      id: before ? before.id : nextId("pi"),
      kind: it.kind === "RESERVATION" ? "RESERVATION" : "FREE",
      placeId: it.placeId,
      stayMinutes: Math.max(10, Math.min(600, Number(it.stayMinutes) || 60)),
      visited: !!it.visited,
      reservation,
    };
  });
}

export const planRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/plans",
    delay: 250,
    handler: () =>
      db.plans
        .filter((p) => p.ownerId === ME_ID)
        .sort((a, b) => a.date.localeCompare(b.date))
        .map(planDto),
  },
  {
    method: "POST",
    path: "/api/plans",
    delay: 500,
    handler: ({ body }) => {
      if (!body?.title || !body?.date) throw new HttpError(400, "REQUIRED", "제목과 날짜를 입력해 주세요.");
      const plan: PlanRow = {
        id: nextId("plan"),
        ownerId: ME_ID,
        title: String(body.title),
        date: String(body.date),
        region: String(body.region ?? ""),
        startPlaceId: findPlace(body.startPlaceId) ? body.startPlaceId : "p-seongsu-st",
        startTime: body.startTime ?? "10:00",
        endTime: body.endTime ?? "20:00",
        memo: String(body.memo ?? ""),
        status: "UPCOMING",
        items: parseItems(body.items ?? []),
      };
      db.plans.push(plan);
      return planDto(plan);
    },
  },
  { method: "GET", path: "/api/plans/:id", handler: ({ params }) => planDto(planOr404(params.id)) },
  {
    method: "PUT",
    path: "/api/plans/:id",
    handler: ({ params, body }) => {
      const plan = planOr404(params.id);
      if (body?.items) plan.items = parseItems(body.items, plan.items);
      if (typeof body?.title === "string") plan.title = body.title;
      if (typeof body?.endTime === "string") plan.endTime = body.endTime;
      return planDto(plan);
    },
  },
  {
    method: "DELETE",
    path: "/api/plans/:id",
    handler: ({ params }) => {
      planOr404(params.id);
      db.plans = db.plans.filter((p) => p.id !== params.id);
      return undefined;
    },
  },
  {
    /** 방문 기록: 방문한 장소는 내 지도에서도 '방문 완료'로 바뀝니다. */
    method: "POST",
    path: "/api/plans/:id/visits",
    handler: ({ params, body }) => {
      const plan = planOr404(params.id);
      const visited: string[] = body?.visitedItemIds ?? [];
      for (const item of plan.items) {
        item.visited = visited.includes(item.id);
        if (item.visited) {
          const saved = db.savedPlaces.find((s) => s.ownerId === ME_ID && s.placeId === item.placeId);
          if (saved) saved.visitStatus = "VISITED";
        }
      }
      plan.status = "DONE";
      return planDto(plan);
    },
  },
  {
    /**
     * 이동 시간 (예시 계산). 실제로는 길찾기 API 결과로 바뀝니다.
     * 계산 중 상태를 확인할 수 있도록 일부러 지연합니다.
     */
    method: "GET",
    path: "/api/travel-time",
    delay: 0,
    handler: async ({ query }) => {
      const from = findPlace(query.get("from") ?? "") ?? notFound("출발 장소");
      const to = findPlace(query.get("to") ?? "") ?? notFound("도착 장소");
      await sleep(500 + Math.random() * 500);
      const meters = Math.round(distanceMeters(from, to) * 1.3);
      const walk = meters <= 2000;
      const minutes = walk ? Math.max(3, Math.round(meters / 75)) : Math.round(12 + meters / 333);
      return { fromId: from.id, toId: to.id, minutes, distanceMeters: meters, mode: walk ? "WALK" : "TRANSIT" };
    },
  },
];
