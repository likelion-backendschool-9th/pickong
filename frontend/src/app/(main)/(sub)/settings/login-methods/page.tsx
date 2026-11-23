import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/shared/ui/display";
import { LoginMethodsScreen } from "./LoginMethodsScreen";

export const metadata: Metadata = { title: "로그인 수단 관리" };

export default function LoginMethodsPage() {
  return (
    <Suspense fallback={<Skeleton className="h-dvh rounded-none" />}>
      <LoginMethodsScreen />
    </Suspense>
  );
}
