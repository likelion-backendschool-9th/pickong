import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function TagNotFound() {
  return (
    <EmptyState title="태그를 찾을 수 없어요" className="pt-24">
      <Link href={routes.tags()} className="text-[14px] font-bold text-primary-deep">
        내 태그로 가기
      </Link>
    </EmptyState>
  );
}
