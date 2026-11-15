import { db, findPlace, ME_ID, nextId, placeDto } from "../db.ts";
import { HttpError, notFound, type Route } from "../router.ts";

/**
 * AI 링크 장소 가져오기 mock.
 * 실제로는 백엔드가 비동기로 콘텐츠를 분석하고, 프론트는 작업 상태를 polling합니다.
 * - URL에 "private" 또는 "login"이 들어 있으면 접근 실패로 응답합니다.
 */
const STEP_MS = 900;
const STEPS = 4;

interface Job {
  id: string;
  source: { type: "URL" | "TEXT"; value: string };
  startedAt: number;
  fail: boolean;
}

const jobs = new Map<string, Job>();

function candidate(
  extractedName: string,
  extractedAddress: string,
  matchedPlaceId: string | null,
  opts: { confidence: number; alternatives?: string[]; duplicateOf?: string | null } = { confidence: 0.9 },
) {
  const matched = matchedPlaceId ? findPlace(matchedPlaceId)! : null;
  return {
    tempId: nextId("cand"),
    extractedName,
    extractedAddress,
    matchedPlace: matched ? placeDto(matched) : null,
    alternatives: (opts.alternatives ?? []).map((id) => placeDto(findPlace(id)!)),
    confidence: opts.confidence,
    needsReview: !matched || opts.confidence < 0.7,
    duplicateOf: opts.duplicateOf ?? null,
    alreadySaved: !!matched && db.savedPlaces.some((s) => s.ownerId === ME_ID && s.placeId === matched.id),
  };
}

function buildCandidates(job: Job) {
  if (job.source.type === "URL") {
    const onyu = candidate("카페 온유", "성수동 · 지도 위치 확인됨", "p-onyu", { confidence: 0.95 });
    return [
      onyu,
      candidate("소품샵 모모", "성수동 · 지도 위치 확인됨", "p-momo", { confidence: 0.92 }),
      candidate("노을 전망대", "위치 확인 필요 · 비슷한 장소가 여러 곳 있어요", null, {
        confidence: 0.4,
        alternatives: ["p-forest-view", "p-noeul"],
      }),
      candidate("카페 온유", "본문에서 한 번 더 언급", "p-onyu", { confidence: 0.95, duplicateOf: onyu.tempId }),
    ];
  }

  const text = job.source.value.replace(/\s+/g, "");
  const found = db.places
    .filter((p) => !p.isCustom && p.category !== "STATION")
    .filter((p) => text.includes(p.name.replace(/\s+/g, "")))
    .map((p) => candidate(p.name, `${p.region} · 지도 위치 확인됨`, p.id, { confidence: 0.9 }));
  if (text.includes("전망대") && !found.some((c) => c.matchedPlace?.category === "PHOTO_SPOT")) {
    found.push(
      candidate("전망대", "위치 확인 필요 · 비슷한 장소가 여러 곳 있어요", null, {
        confidence: 0.4,
        alternatives: ["p-forest-view", "p-noeul"],
      }),
    );
  }
  return found;
}

function jobDto(job: Job) {
  const elapsed = Date.now() - job.startedAt;
  const step = Math.min(STEPS, Math.floor(elapsed / STEP_MS));
  const base = { id: job.id, source: job.source, totalSteps: STEPS };

  if (job.fail && step >= 1) {
    return {
      ...base,
      status: "FAILED" as const,
      step: 1,
      failureReason: "로그인이 필요하거나 비공개인 콘텐츠라 열 수 없어요.",
      candidates: [],
    };
  }
  if (step < STEPS) return { ...base, status: "RUNNING" as const, step, failureReason: null, candidates: [] };
  return { ...base, status: "DONE" as const, step: STEPS, failureReason: null, candidates: buildCandidates(job) };
}

export const importRoutes: Route[] = [
  {
    method: "POST",
    path: "/api/imports",
    handler: ({ body }) => {
      const url = typeof body?.url === "string" ? body.url.trim() : "";
      const text = typeof body?.text === "string" ? body.text.trim() : "";
      if (!url && !text) throw new HttpError(400, "EMPTY", "링크나 텍스트를 입력해 주세요.");
      if (url && !/^https?:\/\/\S+\.\S+/.test(url)) {
        throw new HttpError(400, "BAD_URL", "링크 형식을 확인해 주세요. (https://로 시작)");
      }
      const job: Job = {
        id: nextId("import"),
        source: url ? { type: "URL", value: url } : { type: "TEXT", value: text },
        startedAt: Date.now(),
        fail: !!url && /private|login/i.test(url),
      };
      jobs.set(job.id, job);
      return jobDto(job);
    },
  },
  {
    method: "GET",
    path: "/api/imports/:id",
    delay: 80,
    handler: ({ params }) => {
      const job = jobs.get(params.id) ?? notFound("가져오기 작업");
      return jobDto(job);
    },
  },
  {
    method: "DELETE",
    path: "/api/imports/:id",
    handler: ({ params }) => {
      jobs.delete(params.id);
      return undefined;
    },
  },
];
