import { toPlace } from "@/entities/place";
import type { Plan, PlanItem } from "../model/types";
import type { PlanDto, PlanItemDto } from "./dto";

const toPlanItem = (dto: PlanItemDto): PlanItem => ({
  id: dto.id,
  place: toPlace(dto.place),
  fixedTime: dto.fixedTime,
  visited: dto.visited,
});

export const toPlan = (dto: PlanDto): Plan => ({
  id: dto.id,
  title: dto.title,
  date: dto.date,
  region: dto.region,
  memo: dto.memo ?? "",
  status: dto.status,
  items: dto.items.map(toPlanItem),
});

/** 저장 요청 본문: 장소는 id만 보냅니다. 새 항목(tmp-)은 서버가 id를 만듭니다. */
export const toPlanItemInput = (item: PlanItem) => ({
  id: item.id,
  placeId: item.place.id,
  fixedTime: item.fixedTime,
  visited: item.visited,
});
