import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function CheckoutNotFound() {
  return (
    <EmptyState title="결제할 컬렉션을 찾을 수 없어요" className="pt-24">
      <Link href={routes.feed()} className="text-[14px] font-bold text-primary-deep">
        홈으로 가기
      </Link>
    </EmptyState>
  );
}
