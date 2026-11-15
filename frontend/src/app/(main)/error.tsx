"use client";

import { Button } from "@/shared/ui/Button";
import { EmptyState } from "@/shared/ui/display";

export default function MainError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="flex min-h-dvh items-center">
      <EmptyState title="화면을 불러오지 못했어요" description="잠시 후 다시 시도해 주세요." className="w-full">
        <Button block onClick={reset}>
          다시 시도
        </Button>
      </EmptyState>
    </div>
  );
}
