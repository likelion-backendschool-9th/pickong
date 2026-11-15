import { MapPinPlus } from "lucide-react";
import { routes } from "@/shared/config/routes";
import { MapView } from "@/shared/map";
import { ButtonLink } from "@/shared/ui/Button";

/** 첫 사용: 저장 장소가 없을 때 시작 경로 두 가지 */
export function EmptyMap() {
  return (
    <div className="relative">
      <MapView className="h-[calc(100dvh-260px)] min-h-[420px] opacity-70" showZoom={false} />
      <div className="absolute inset-x-4 top-1/2 -translate-y-1/2 rounded-card bg-bg p-6 text-center shadow-[0_2px_16px_rgba(0,0,0,0.1)]">
        <span className="mx-auto mb-3 flex size-12 items-center justify-center rounded-full bg-soft text-primary-deep">
          <MapPinPlus size={22} />
        </span>
        <p className="text-[17px] font-bold">아직 저장한 장소가 없어요</p>
        <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">
          피드에서 마음에 드는 장소를 찜콩하거나,
          <br />
          직접 장소를 추가해 나만의 지도를 시작해요.
        </p>
        <div className="mt-5 flex flex-col gap-2">
          <ButtonLink href={routes.feed()} block>피드에서 장소 발견하기</ButtonLink>
          <ButtonLink href={routes.newPlace()} variant="secondary" block>직접 추가 / 링크로 가져오기</ButtonLink>
        </div>
      </div>
    </div>
  );
}
