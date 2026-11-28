"use client";

import { MapView } from "@/shared/map";
import { Button } from "@/shared/ui/Button";
import { Badge } from "@/shared/ui/display";
import { DAY_LABELS, formatAdminBusinessHours, is24Hours } from "../_lib/businessHours";
import { adminPlaceCategoryText, adminPlaceSourceText, isKnownAdminPlaceSource } from "../_lib/filterAdminPlaces";
import { ADMIN_PLACE_DAY_ORDER } from "../_lib/hoursDraft";
import { canManageAdminPlace, type AdminPlaceDetail } from "../_lib/models";
import { AdminPlacePhoto } from "./AdminPlaceCard";

function hourText(row: { closed: boolean; openTime: number | null; closeTime: number | null } | undefined) {
  if (!row) return "미등록";
  if (row.closed) return "휴무";
  if (row.openTime == null || row.closeTime == null) return "시간 확인 필요";
  if (is24Hours(row.openTime, row.closeTime)) return "24시간 영업";
  return formatAdminBusinessHours({ closed: false, openTime: row.openTime, closeTime: row.closeTime });
}

export function AdminPlaceDetailView({
  place,
  onEdit,
  onDelete,
}: {
  place: AdminPlaceDetail;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const manageable = canManageAdminPlace(place.sourceType, place.visibility);
  return (
    <div className="space-y-6 px-5 pt-4 pb-8">
      <AdminPlacePhoto imageUrl={place.imageUrl} name={place.name} className="h-48 w-full rounded-card" />
      <div className="space-y-2">
        <h2 className="text-[20px] font-bold break-words">{place.name}</h2>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="neutral">{adminPlaceCategoryText(place.category)}</Badge>
          <Badge tone={place.sourceType === "SYSTEM" ? "lime" : "neutral"}>{adminPlaceSourceText(place.sourceType)}</Badge>
          {!isKnownAdminPlaceSource(place.sourceType) && <span className="text-[12px] text-ink-faint">{place.sourceType}</span>}
        </div>
        <p className="text-[13px] text-ink-sub">{manageable ? "관리 가능한 시스템 장소예요." : "시스템 제공 장소가 아니므로 조회만 할 수 있어요."}</p>
        <p className="text-[12px] text-ink-faint">공개 범위 {place.visibility === "PUBLIC" ? "공개" : (place.visibility ?? "없음")}</p>
        <p className="text-[12px] break-all text-ink-faint">ID {place.id}</p>
      </div>
      <section className="space-y-1">
        <h3 className="text-[15px] font-bold">주소</h3>
        <p className="text-[14px] break-words text-ink-sub">{place.address?.trim() ? place.address : "주소 미등록"}</p>
      </section>
      <section className="space-y-2">
        <h3 className="text-[15px] font-bold">위치</h3>
        <p className="text-[13px] text-ink-sub tabular-nums">{place.location ? `${place.location.lat}, ${place.location.lng}` : "좌표 없음"}</p>
        {place.location && <MapView className="h-48 rounded-card" center={place.location} metersPerPixel={2.4} ariaLabel="장소 위치" />}
      </section>
      <section className="space-y-2">
        <h3 className="text-[15px] font-bold">영업시간</h3>
        <ul className="space-y-1">
          {ADMIN_PLACE_DAY_ORDER.map((day) => {
            const row = place.businessHours.find((item) => item.dayOfWeek === day);
            return (
              <li key={day} className="flex justify-between gap-3 text-[14px]">
                <span className="font-bold">{DAY_LABELS[day]}</span>
                <span className="text-ink-sub">{hourText(row)}</span>
              </li>
            );
          })}
        </ul>
      </section>
      <section className="space-y-2">
        <h3 className="text-[15px] font-bold">예외 영업시간</h3>
        {place.hoursExceptions.length === 0 ? (
          <p className="text-[14px] text-ink-sub">등록된 예외가 없어요.</p>
        ) : (
          <ul className="space-y-2">
            {place.hoursExceptions.map((row) => (
              <li key={`${row.exceptionDate}:${row.note ?? ""}`} className="rounded-card border border-line p-3 text-[14px]">
                <p className="font-bold">{row.exceptionDate}</p>
                <p className="mt-1 text-ink-sub">{row.closed || row.openTime == null || row.closeTime == null ? "휴무" : hourText(row)}</p>
                {row.note && <p className="mt-1 text-[13px] text-ink-faint">{row.note}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>
      <div className="flex flex-wrap gap-2">
        {manageable && (
          <>
            <Button type="button" size="md" onClick={onEdit}>
              수정
            </Button>
            <Button type="button" variant="danger" size="md" onClick={onDelete}>
              삭제
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
