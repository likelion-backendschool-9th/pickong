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
  /** 유료 컬렉션 가격(원). null이면 무료 */
  price: number | null;
  /** 내가 구매한 유료 컬렉션 */
  isPurchased: boolean;
  /** 유료인데 내 것도 아니고 사지 않아서 미리보기만 볼 수 있음 */
  isLocked: boolean;
}

export interface CollectionDetail extends Collection {
  /** 잠긴 컬렉션이면 미리보기 장소만 들어 있습니다. */
  places: (Place & { savedByMe: boolean })[];
  /** 잠겨서 보이지 않는 장소 수 */
  lockedPlaceCount: number;
}

export const PIN_COLORS: { value: PinColor; label: string }[] = [
  { value: "LIME", label: "라임" },
  { value: "GREEN", label: "그린" },
  { value: "BROWN", label: "브라운" },
];
