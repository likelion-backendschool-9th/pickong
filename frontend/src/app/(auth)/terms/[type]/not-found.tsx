import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function TermsNotFound() {
  return (
    <EmptyState title="약관을 찾을 수 없어요" className="pt-24">
      <Link href={routes.signup()} className="text-[14px] font-bold text-primary-deep">
        회원가입으로 돌아가기
      </Link>
    </EmptyState>
  );
}
