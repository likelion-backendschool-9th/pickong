import { toPlace } from "@/entities/place";
import type { Plan, PlanItem } from "../model/types";
import type { PlanDto, PlanItemDto } from "./dto";

const toPlanItem = (dto: PlanItemDto): PlanItem => ({
  id: dto.id,
  kind: dto.kind,
  place: toPlace(dto.place),
  stayMinutes: dto.stayMinutes,
  visited: dto.visited,
  reservation: dto.reservation,
});

export const toPlan = (dto: PlanDto): Plan => ({
  id: dto.id,
  title: dto.title,
  date: dto.date,
  region: dto.region,
  startPlace: toPlace(dto.startPlace),
  startTime: dto.startTime,
  endTime: dto.endTime,
  memo: dto.memo ?? "",
  status: dto.status,
  items: dto.items.map(toPlanItem),
});

/** 저장 요청 본문: 장소는 id만 보냅니다. */
export const toPlanItemInput = (item: PlanItem) => ({
  id: item.id,
  kind: item.kind,
  placeId: item.place.id,
  stayMinutes: item.stayMinutes,
  visited: item.visited,
  reservation: item.reservation,
});
