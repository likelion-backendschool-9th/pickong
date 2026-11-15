import type { Visibility } from "@/shared/config/visibility";
import type { Place } from "@/entities/place";
import type { UserSummary } from "@/entities/user";

export type PinColor = "LIME" | "GREEN" | "BROWN";

export interface Collection {
  id: string;
  owner: UserSummary;
  title: string;
  description: string;
  coverImageUrl: string | null;
  visibility: Visibility;
  pinColor: PinColor;
  placeCount: number;
  isMine: boolean;
}

export interface CollectionDetail extends Collection {
  places: (Place & { savedByMe: boolean })[];
}

export const PIN_COLORS: { value: PinColor; label: string }[] = [
  { value: "LIME", label: "라임" },
  { value: "GREEN", label: "그린" },
  { value: "BROWN", label: "브라운" },
];
