import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function PurchaseNotFound() {
  return (
    <EmptyState title="구매 내역을 찾을 수 없어요" className="pt-24">
      <Link href={routes.purchases()} className="text-[14px] font-bold text-primary-deep">
        구매 내역으로 가기
      </Link>
    </EmptyState>
  );
}
