import { db, distanceMeters, findUser, ME_ID, nextId } from "../db.ts";
import { placeSubmissions, type PlaceSubmissionRow } from "../fixtures/placeSubmissions.ts";
import type { PlaceCategory, PlaceRow } from "../fixtures/types.ts";
import { HttpError } from "../router.ts";
import type { Route } from "../router.ts";

/**
 * 장소 제보. 승인된 장소만 db.places에 넣습니다.
 * 대기·반려는 공개 지도 조회(GET /api/v1/places)에 나오지 않습니다.
 * 이 배열은 프로세스 메모리에만 있어서, mock 서버를 다시 시작하면 시드로 돌아갑니다.
 */
let submissions: PlaceSubmissionRow[] = structuredClone(placeSubmissions);

const CATEGORIES = new Set<PlaceCategory>([
  "CAFE",
  "RESTAURANT",
  "PHOTO_SPOT",
  "SHOP",
  "EXHIBITION",
  "STREET",
  "PARK",
  "ATTRACTION",
  "STATION",
  "ETC",
]);

function placeFrom(row: PlaceSubmissionRow, id: string): PlaceRow {
  return {
    id,
    name: row.name,
    address: row.address,
    region: row.region,
    lat: row.lat,
    lng: row.lng,
    category: row.category,
    isCustom: true,
    ownerId: row.submitterId,
    visibility: "PUBLIC",
    description: row.description,
    imageUrl: row.imageUrl,
    businessHours: null,
  };
}

for (const row of submissions) {
  if (row.status !== "APPROVED" || !row.placeId) continue;
  if (db.places.some((place) => place.id === row.placeId)) continue;
  db.places.push(placeFrom(row, row.placeId));
}

function toDto(row: PlaceSubmissionRow) {
  const user = findUser(row.submitterId);
  return {
    id: row.id,
    name: row.name,
    category: row.category,
    address: row.address,
    lat: row.lat,
    lng: row.lng,
    description: row.description,
    imageUrl: row.imageUrl,
    submitter: user ? { id: user.id, username: user.username, nickname: user.nickname } : null,
    submittedAt: row.submittedAt,
    status: row.status,
    reviewedAt: row.reviewedAt,
    rejectReason: row.rejectReason,
    placeId: row.placeId,
  };
}

function findSubmission(id: string) {
  const row = submissions.find((item) => item.id === id);
  if (!row) throw new HttpError(404, "NOT_FOUND", "제보를 찾을 수 없어요.");
  return row;
}

function assertPending(row: PlaceSubmissionRow) {
  if (row.status !== "PENDING") throw new HttpError(409, "ALREADY_REVIEWED", "이미 처리한 제보예요.");
}

function assertLocation(row: PlaceSubmissionRow) {
  if (!Number.isFinite(row.lat) || !Number.isFinite(row.lng) || row.lat < -90 || row.lat > 90 || row.lng < -180 || row.lng > 180) {
    throw new HttpError(400, "NO_LOCATION", "좌표가 없어 승인할 수 없어요.");
  }
}

export const placeSubmissionRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/admin/place-submissions",
    delay: 250,
    handler: () => submissions.map(toDto),
  },
  {
    method: "GET",
    path: "/api/v1/admin/place-submissions/:id",
    handler: ({ params }) => toDto(findSubmission(params.id)),
  },
  {
    method: "POST",
    path: "/api/v1/admin/place-submissions/:id/approve",
    delay: 300,
    handler: ({ params }) => {
      const row = findSubmission(params.id);
      assertPending(row);
      assertLocation(row);
      const placeId = nextId("p");
      db.places.push(placeFrom(row, placeId));
      row.status = "APPROVED";
      row.reviewedAt = new Date().toISOString();
      row.rejectReason = null;
      row.placeId = placeId;
      return toDto(row);
    },
  },
  {
    method: "POST",
    path: "/api/v1/admin/place-submissions/:id/reject",
    delay: 300,
    handler: ({ params, body }) => {
      const row = findSubmission(params.id);
      assertPending(row);
      const reason = String(body?.reason ?? "").trim();
      if (!reason) throw new HttpError(400, "NO_REASON", "반려 사유를 입력해 주세요.");
      row.status = "REJECTED";
      row.reviewedAt = new Date().toISOString();
      row.rejectReason = reason;
      row.placeId = null;
      return toDto(row);
    },
  },
  {
    /** 사용자가 좌표로 장소를 제보. 공개 장소 목록에는 넣지 않습니다. */
    method: "POST",
    path: "/api/v1/place-submissions",
    delay: 400,
    handler: ({ body }) => {
      const name = String(body?.name ?? "").trim();
      if (!name) throw new HttpError(400, "NO_NAME", "장소 이름을 입력해 주세요.");
      if (typeof body?.lat !== "number" || typeof body?.lng !== "number") {
        throw new HttpError(400, "NO_LOCATION", "위치를 지정해 주세요.");
      }
      const category = CATEGORIES.has(body?.category) ? body.category : "ETC";
      const nearest = [...db.places]
        .filter((place) => !place.isCustom)
        .sort((a, b) => distanceMeters(a, { lat: body.lat, lng: body.lng }) - distanceMeters(b, { lat: body.lat, lng: body.lng }))[0];
      const row: PlaceSubmissionRow = {
        id: nextId("ps"),
        name,
        category,
        address: String(body?.address ?? "").trim() || `${nearest?.region ?? "선택한 위치"} 부근 · 제보된 좌표`,
        region: String(body?.region ?? "").trim() || nearest?.region || "",
        lat: body.lat,
        lng: body.lng,
        description: String(body?.description ?? "").slice(0, 200),
        imageUrl: body?.imageUrl ?? null,
        submitterId: ME_ID,
        submittedAt: new Date().toISOString(),
        status: "PENDING",
        reviewedAt: null,
        rejectReason: null,
        placeId: null,
      };
      submissions = [row, ...submissions];
      return toDto(row);
    },
  },
];
