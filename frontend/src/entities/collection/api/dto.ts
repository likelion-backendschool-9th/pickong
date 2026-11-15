import type { Visibility } from "@/shared/config/visibility";
import type { PlaceDto } from "@/entities/place";
import type { UserSummaryDto } from "@/entities/user";
import type { PinColor } from "../model";

export interface CollectionDto {
  id: string;
  owner: UserSummaryDto;
  title: string;
  description: string | null;
  coverImageUrl: string | null;
  visibility: Visibility;
  pinColor: PinColor;
  placeCount: number;
  isMine: boolean;
}

export interface CollectionDetailDto extends CollectionDto {
  places: (PlaceDto & { savedByMe: boolean })[];
}
