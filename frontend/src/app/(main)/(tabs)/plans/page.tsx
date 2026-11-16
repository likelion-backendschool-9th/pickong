"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useMe } from "@/entities/user";
import { routes } from "@/shared/config/routes";
import { Skeleton } from "@/shared/ui/display";

/** 일정은 내 프로필의 일정 탭으로 옮겼습니다. 예전 주소(/plans)로 들어오면 그쪽으로 보냅니다. */
export default function PlansPage() {
  const { data: me } = useMe();
  const router = useRouter();
  useEffect(() => {
    if (me) router.replace(routes.myPlans(me.username));
  }, [me, router]);
  return <Skeleton className="h-dvh rounded-none" />;
}
