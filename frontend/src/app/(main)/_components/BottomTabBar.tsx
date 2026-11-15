"use client";

import { CalendarDays, House, Map as MapIcon, Plus, UserRound } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useMe } from "@/entities/user";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { ComposeMenu } from "./ComposeMenu";

/** 하단 내비게이션: 홈 / 내 지도 / 추가(+) / 일정 / 프로필 */
export function BottomTabBar() {
  const pathname = usePathname();
  const { data: me } = useMe();
  const [composeOpen, setComposeOpen] = useState(false);
  const profileHref = me ? routes.user(me.username) : routes.settings();

  const tabs = [
    { href: routes.feed(), label: "홈", icon: House, active: pathname.startsWith("/feed") },
    {
      href: routes.map(),
      label: "내 지도",
      icon: MapIcon,
      active: pathname.startsWith("/map") || pathname === "/collections",
    },
    null,
    { href: routes.plans(), label: "일정", icon: CalendarDays, active: pathname.startsWith("/plans") },
    {
      href: profileHref,
      label: "프로필",
      icon: UserRound,
      active: !!me && pathname.startsWith(routes.user(me.username)),
    },
  ];

  return (
    <>
      <nav
        aria-label="주요 메뉴"
        className="fixed inset-x-0 bottom-0 z-40 mx-auto w-full max-w-[480px] border-t border-line bg-bg pb-safe"
      >
        <ul className="grid h-[60px] grid-cols-5">
          {tabs.map((tab, i) =>
            tab === null ? (
              <li key="compose" className="flex items-center justify-center">
                <button
                  type="button"
                  onClick={() => setComposeOpen(true)}
                  aria-label="추가"
                  aria-haspopup="dialog"
                  className="flex flex-col items-center gap-0.5 text-ink-sub"
                >
                  <span className="flex size-9 items-center justify-center rounded-full bg-primary text-on-primary">
                    <Plus size={22} strokeWidth={2.2} />
                  </span>
                </button>
              </li>
            ) : (
              <li key={i}>
                <Link
                  href={tab.href}
                  aria-current={tab.active ? "page" : undefined}
                  className={cn(
                    "flex h-full flex-col items-center justify-center gap-1 text-[10px]",
                    tab.active ? "font-bold text-primary-deep" : "text-ink-sub",
                  )}
                >
                  <tab.icon size={22} strokeWidth={tab.active ? 2 : 1.6} />
                  {tab.label}
                </Link>
              </li>
            ),
          )}
        </ul>
      </nav>
      <ComposeMenu open={composeOpen} onClose={() => setComposeOpen(false)} />
    </>
  );
}
