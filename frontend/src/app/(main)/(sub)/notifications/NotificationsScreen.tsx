"use client";

import { AlertTriangle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatRelative } from "@/shared/lib/date";
import { Tabs } from "@/shared/ui/controls";
import { Avatar, EmptyState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useNotifications, type AppNotification } from "./_api/notificationApi";

type Filter = "all" | "social" | "plan";

export function NotificationsScreen() {
  const { data, isPending } = useNotifications();
  const [filter, setFilter] = useState<Filter>("all");
  const list = (data ?? []).filter((n) =>
    filter === "all" ? true : filter === "plan" ? n.type === "PLAN_REMINDER" : n.type !== "PLAN_REMINDER",
  );

  return (
    <div>
      <TopBar title="알림" backHref={routes.feed()} />
      <div className="border-b border-line px-5">
        <Tabs
          size="sm"
          items={[
            { value: "all", label: "전체" },
            { value: "social", label: "소셜" },
            { value: "plan", label: "일정" },
          ]}
          value={filter}
          onChange={setFilter}
        />
      </div>
      {isPending ? (
        <Skeleton className="m-5 h-40" />
      ) : list.length === 0 ? (
        <EmptyState title="새 알림이 없어요" />
      ) : (
        <ul className="px-5">
          {list.map((n) => (
            <li key={n.id}>
              <NotificationRow n={n} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NotificationRow({ n }: { n: AppNotification }) {
  return (
    <Link href={n.link} className={cn("-mx-5 flex items-start gap-3 px-5 py-3.5 active:bg-fill", !n.read && "bg-soft")}>
      {n.actor ? (
        <Avatar src={n.actor.profileImageUrl} name={n.actor.nickname} size={40} />
      ) : (
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-warning-soft text-warning">
          <AlertTriangle size={18} />
        </span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[14px] leading-snug">{n.message}</p>
        {n.detail && <p className="mt-0.5 truncate text-[12px] text-ink-sub">{n.detail}</p>}
        <p className="mt-1 text-[11px] text-ink-faint">{formatRelative(n.createdAt)}</p>
      </div>
    </Link>
  );
}
