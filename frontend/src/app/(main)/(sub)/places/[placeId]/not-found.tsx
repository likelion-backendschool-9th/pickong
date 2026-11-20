import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function PlaceNotFound() {
  return (
    <EmptyState title="장소를 찾을 수 없어요" description="삭제됐거나 볼 수 없는 장소예요." className="pt-24">
      <Link href={routes.map()} className="text-[14px] font-bold text-primary-deep">
        내 지도로 가기
      </Link>
    </EmptyState>
  );
}
