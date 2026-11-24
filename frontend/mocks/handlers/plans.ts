import { db, findPlace, ME_ID, nextId, planDto } from "../db.ts";
import type { PlanItemRow, PlanRow } from "../fixtures/types.ts";
import { HttpError, notFound, type Route } from "../router.ts";

const planOr404 = (id: string) => {
  const plan = db.plans.find((p) => p.id === id && p.ownerId === ME_ID);
  return plan ?? notFound("일정");
};

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

/** 고정 시간은 HH:mm 또는 null */
function parseFixedTime(raw: unknown): string | null {
  if (raw === null || raw === undefined || raw === "") return null;
  if (typeof raw !== "string" || !TIME_RE.test(raw)) throw new HttpError(400, "BAD_TIME", "고정 시간 형식이 올바르지 않아요.");
  return raw;
}

function parseItems(raw: unknown, previous: PlanItemRow[] = []): PlanItemRow[] {
  if (!Array.isArray(raw)) throw new HttpError(400, "BAD_ITEMS", "일정 항목 형식이 올바르지 않아요.");
  return raw.map((it) => {
    if (!findPlace(it?.placeId)) throw new HttpError(400, "BAD_PLACE", "장소를 찾을 수 없어요.");
    const before = previous.find((p) => p.id === it.id);
    return {
      id: before ? before.id : nextId("pi"),
      placeId: it.placeId,
      fixedTime: parseFixedTime(it.fixedTime),
      visited: !!it.visited,
    };
  });
}

/** 방문 완료로 표시: 내 지도에 있으면 상태만 바꾸고, 없으면 ‘방문 완료’로 저장합니다. */
function markVisited(placeId: string) {
  const saved = db.savedPlaces.find((s) => s.ownerId === ME_ID && s.placeId === placeId);
  if (saved) {
    saved.visitStatus = "VISITED";
    return;
  }
  db.savedPlaces.push({
    id: nextId("sp"),
    ownerId: ME_ID,
    placeId,
    collectionIds: [],
    visitStatus: "VISITED",
    memo: "",
    savedAt: new Date().toISOString(),
    source: { type: "SEARCH", refId: null, label: "일정 방문 기록에서 추가" },
  });
}

export const planRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/plans",
    delay: 250,
    handler: () =>
      db.plans
        .filter((p) => p.ownerId === ME_ID)
        .sort((a, b) => a.date.localeCompare(b.date))
        .map(planDto),
  },
  {
    /** 새 일정은 ‘계획 중’으로 시작합니다. */
    method: "POST",
    path: "/api/v1/plans",
    delay: 400,
    handler: ({ body }) => {
      if (!body?.title || !body?.date) throw new HttpError(400, "REQUIRED", "제목과 날짜를 입력해 주세요.");
      const plan: PlanRow = {
        id: nextId("plan"),
        ownerId: ME_ID,
        title: String(body.title),
        date: String(body.date),
        region: String(body.region ?? ""),
        memo: String(body.memo ?? ""),
        status: "PLANNING",
        items: parseItems(body.items ?? []),
      };
      db.plans.push(plan);
      return planDto(plan);
    },
  },
  { method: "GET", path: "/api/v1/plans/:id", handler: ({ params }) => planDto(planOr404(params.id)) },
  {
    /** 항목 저장 · 계획 완료(status: UPCOMING) · 다시 수정(status: PLANNING) */
    method: "PUT",
    path: "/api/v1/plans/:id",
    handler: ({ params, body }) => {
      const plan = planOr404(params.id);
      if (body?.items) {
        if (plan.status === "DONE") throw new HttpError(409, "PLAN_DONE", "다녀온 일정은 수정할 수 없어요.");
        plan.items = parseItems(body.items, plan.items);
      }
      if (typeof body?.title === "string") plan.title = body.title;
      if (body?.status !== undefined) {
        if (body.status !== "PLANNING" && body.status !== "UPCOMING") throw new HttpError(400, "BAD_STATUS", "일정 상태를 확인해 주세요.");
        if (plan.status === "DONE") throw new HttpError(409, "PLAN_DONE", "다녀온 일정은 수정할 수 없어요.");
        if (body.status === "UPCOMING" && plan.items.length === 0) throw new HttpError(400, "EMPTY_PLAN", "장소를 1곳 이상 넣어 주세요.");
        plan.status = body.status;
      }
      return planDto(plan);
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/plans/:id",
    handler: ({ params }) => {
      planOr404(params.id);
      db.plans = db.plans.filter((p) => p.id !== params.id);
      return undefined;
    },
  },
  {
    /**
     * 방문 기록: 체크한 곳과 일정에 없었지만 다녀온 곳(extraPlaceIds)을 함께 기록합니다.
     * 추가한 곳은 일정 끝에 붙고, 다녀온 곳은 내 지도에서 ‘방문 완료’가 됩니다.
     */
    method: "POST",
    path: "/api/v1/plans/:id/visits",
    handler: ({ params, body }) => {
      const plan = planOr404(params.id);
      const visited: string[] = Array.isArray(body?.visitedItemIds) ? body.visitedItemIds : [];
      const extras: string[] = Array.isArray(body?.extraPlaceIds) ? body.extraPlaceIds : [];
      for (const placeId of extras) {
        if (!findPlace(placeId)) throw new HttpError(400, "BAD_PLACE", "추가한 장소를 찾을 수 없어요.");
      }
      for (const item of plan.items) {
        item.visited = visited.includes(item.id);
        if (item.visited) markVisited(item.placeId);
      }
      for (const placeId of extras) {
        plan.items.push({ id: nextId("pi"), placeId, fixedTime: null, visited: true });
        markVisited(placeId);
      }
      plan.status = "DONE";
      return planDto(plan);
    },
  },
];
