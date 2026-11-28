import { createAdminPlace, deleteAdminPlace, getAdminPlaceDetail, listAdminPlaceSummaries, updateAdminPlace } from "../lib/adminPlaces.ts";
import { RawResponse, type Route } from "../router.ts";

/** 201 JSON. 공용 라우터의 기본 성공 코드는 200이라 등록만 상태 코드를 직접 지정합니다. */
function created(data: unknown) {
  return new RawResponse(201, { "Content-Type": "application/json; charset=utf-8" }, JSON.stringify(data));
}

/**
 * 관리자 장소 Mock. 회원 /places, 제보 /admin/place-submissions와 경로가 다릅니다.
 * 관리자 로그인 검사는 하지 않습니다. SYSTEM 제한은 업무 규칙 검사용입니다.
 */
export const adminPlaceRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/admin/places",
    delay: 0,
    handler: () => listAdminPlaceSummaries(),
  },
  {
    method: "GET",
    path: "/api/v1/admin/places/:placeId",
    delay: 0,
    handler: ({ params }) => getAdminPlaceDetail(params.placeId),
  },
  {
    method: "POST",
    path: "/api/v1/admin/places",
    delay: 0,
    handler: ({ body }) => created(createAdminPlace(body)),
  },
  {
    method: "PATCH",
    path: "/api/v1/admin/places/:placeId",
    delay: 0,
    handler: ({ params, body }) => updateAdminPlace(params.placeId, body),
  },
  {
    method: "DELETE",
    path: "/api/v1/admin/places/:placeId",
    delay: 0,
    handler: ({ params }) => {
      deleteAdminPlace(params.placeId);
    },
  },
];
