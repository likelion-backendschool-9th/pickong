import type { PlaceDto } from "@/entities/place";
import type { Reservation } from "../model/types";

export interface PlanItemDto {
  id: string;
  kind: "RESERVATION" | "FREE";
  place: PlaceDto;
  stayMinutes: number;
  visited: boolean;
  reservation: Reservation | null;
}

export interface PlanDto {
  id: string;
  title: string;
  date: string;
  region: string;
  startPlace: PlaceDto;
  startTime: string;
  endTime: string;
  memo: string | null;
  status: "UPCOMING" | "DONE";
  items: PlanItemDto[];
}

export interface TravelTimeDto {
  fromId: string;
  toId: string;
  minutes: number;
  distanceMeters: number;
  mode: "WALK" | "TRANSIT";
}

export interface VisitRecordDto {
  planId: string;
  title: string;
  date: string;
  region: string;
  visitedCount: number;
  places: PlaceDto[];
  coverImageUrl: string | null;
}
