import { MapPinOff } from "lucide-react";
import { ButtonLink } from "@/shared/ui/Button";
import { EmptyState } from "@/shared/ui/display";

export default function NotFound() {
  return (
    <div className="flex min-h-dvh items-center">
      <EmptyState icon={<MapPinOff size={24} />} title="페이지를 찾을 수 없어요" description="주소가 바뀌었거나 삭제된 페이지예요." className="w-full">
        <ButtonLink href="/feed" block>
          홈으로
        </ButtonLink>
      </EmptyState>
    </div>
  );
}
