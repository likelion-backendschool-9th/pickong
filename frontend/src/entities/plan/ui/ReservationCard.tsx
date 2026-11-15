import { Lock } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/cn";
import type { PlanItem, ReservationType } from "../model/types";

export const RESERVATION_TYPE_LABEL: Record<ReservationType, string> = {
  RESTAURANT: "식당",
  SHOW: "공연·영화",
  EXHIBITION: "전시",
  STAY: "숙소",
  ETC: "기타",
};

/** 확정 예약 카드: 연한 라임 배경 + 잠금 아이콘. 드래그로 옮길 수 없습니다. */
export function ReservationCard({ item, children, className }: { item: PlanItem; children?: ReactNode; className?: string }) {
  const r = item.reservation!;
  return (
    <div className={cn("rounded-card border border-primary-deep/15 bg-soft px-4 py-3", className)}>
      <div className="flex items-center gap-1.5">
        <Lock size={14} className="text-primary-deep" aria-label="고정된 예약" />
        <span className="text-[15px] font-bold tabular-nums">{r.startTime}</span>
        <span className="truncate text-[15px] font-bold">{item.place.name}</span>
      </div>
      <p className="mt-0.5 text-[12px] text-ink-sub">
        외부 확정 예약 · {RESERVATION_TYPE_LABEL[r.type]} · {r.endTime} 종료 · {r.people}명
        {r.reservationNumber && ` · ${r.reservationNumber}`}
      </p>
      {children}
    </div>
  );
}
