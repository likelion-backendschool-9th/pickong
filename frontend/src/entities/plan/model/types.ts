import type { Place } from "@/entities/place";

export type ReservationType = "RESTAURANT" | "SHOW" | "EXHIBITION" | "STAY" | "ETC";

/** 다른 서비스에서 이미 확정한 예약 기록. 찜콩에서 예약·결제를 진행하지 않습니다. */
export interface Reservation {
  type: ReservationType;
  startTime: string; // HH:mm
  endTime: string; // HH:mm
  people: number;
  reservationNumber: string;
  link: string;
  memo: string;
}

export interface PlanItem {
  id: string;
  /** RESERVATION: 확정 예약 (고정) / FREE: 자유 일정 (순서·체류 시간 조정 가능) */
  kind: "RESERVATION" | "FREE";
  place: Place;
  stayMinutes: number;
  visited: boolean;
  reservation: Reservation | null;
}

export interface Plan {
  id: string;
  title: string;
  date: string; // YYYY-MM-DD
  region: string;
  startPlace: Place;
  startTime: string;
  endTime: string;
  memo: string;
  status: "UPCOMING" | "DONE";
  items: PlanItem[];
}

/** 이전 지점에서 이 장소까지 이동 */
export interface Leg {
  minutes: number;
  /** true: 직선거리 추정치 (계산 전 임시값) / false: 계산 완료값 */
  isEstimate: boolean;
  mode?: "WALK" | "TRANSIT";
}

export type PlanWarning =
  | { type: "CLOSED_DAY" }
  | { type: "AFTER_CLOSE"; arriveAt: number; closeAt: number; lateMinutes: number }
  | { type: "CLOSES_DURING_STAY"; closeAt: number; overMinutes: number }
  | { type: "LATE_FOR_RESERVATION"; arriveAt: number; startAt: number; lateMinutes: number; causeItemId: string | null };

export interface TimelineEntry {
  itemId: string;
  /** 도착 (분) */
  arriveAt: number;
  /** 방문 시작 (예약 시작 또는 오픈 대기 후) */
  startAt: number;
  departAt: number;
  /** 도착 후 시작까지 남는 시간 (빈 시간) */
  waitMinutes: number;
  leg: Leg;
  warnings: PlanWarning[];
  hoursUnknown: boolean;
}

export interface Timeline {
  entries: TimelineEntry[];
  startAt: number;
  endAt: number;
  /** 종료 희망 시간을 넘긴 분 */
  overEndMinutes: number;
  warningCount: number;
}
