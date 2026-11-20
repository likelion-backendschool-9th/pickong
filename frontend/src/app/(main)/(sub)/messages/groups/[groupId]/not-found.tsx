import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { EmptyState } from "@/shared/ui/display";

export default function GroupChatNotFound() {
  return (
    <EmptyState title="채팅방을 찾을 수 없어요" description="나갔거나 사라진 채팅방이에요." className="pt-24">
      <Link href={routes.groupChats()} className="text-[14px] font-bold text-primary-deep">
        그룹 채팅 목록으로
      </Link>
    </EmptyState>
  );
}
