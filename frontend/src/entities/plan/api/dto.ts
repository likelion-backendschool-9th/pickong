import type { PlaceDto } from "@/entities/place";
import type { PlanStatus } from "../model/types";

export interface PlanItemDto {
  id: string;
  place: PlaceDto;
  fixedTime: string | null;
  visited: boolean;
}

export interface PlanDto {
  id: string;
  title: string;
  date: string;
  region: string;
  memo: string | null;
  status: PlanStatus;
  items: PlanItemDto[];
}
