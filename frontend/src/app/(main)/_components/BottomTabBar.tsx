"use client";

import { House, Map as MapIcon, Plus, Send, UserRound } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useConversationRealtime, useUnreadMessageCount } from "@/entities/conversation";
import { useMe } from "@/entities/user";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { emitHomeReselect } from "@/shared/lib/events";
import { ComposeMenu } from "./ComposeMenu";

/** 하단 내비게이션: 홈 / 내 지도 / 추가(+) / DM / 프로필 (일정은 내 프로필의 일정 탭) */
export function BottomTabBar() {
  const pathname = usePathname();
  const params = useSearchParams();
  const { data: me } = useMe();
  /** 이미 피드 목록을 보고 있을 때 홈을 누르면: 이동 대신 맨 위로 + 새로고침 */
  const onFeedList = pathname === routes.feed() && params.get("view") !== "map";
  const [composeOpen, setComposeOpen] = useState(false);
  // 탭 화면에 있는 동안 실시간 연결을 유지해 새 메시지 배지 · 대화 목록이 바로 바뀌게 합니다.
  useConversationRealtime();
  const unread = useUnreadMessageCount();
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
    { href: routes.messages(), label: "DM", icon: Send, active: pathname.startsWith("/messages"), badge: unread },
    {
      href: profileHref,
      label: "프로필",
      icon: UserRound,
      // 일정 화면(/plans/...)도 내 프로필의 일정 탭에서 들어가므로 프로필로 표시
      active: (!!me && pathname.startsWith(routes.user(me.username))) || pathname.startsWith("/plans"),
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
                  aria-label={"badge" in tab && tab.badge ? `${tab.label}, 안 읽은 메시지 ${tab.badge}개` : undefined}
                  onClick={(e) => {
                    if (tab.href === routes.feed() && onFeedList) {
                      e.preventDefault();
                      emitHomeReselect();
                    }
                  }}
                  className={cn(
                    "flex h-full flex-col items-center justify-center gap-1 text-[10px]",
                    tab.active ? "font-bold text-primary-deep" : "text-ink-sub",
                  )}
                >
                  <span className="relative">
                    <tab.icon size={22} strokeWidth={tab.active ? 2 : 1.6} />
                    {"badge" in tab && !!tab.badge && (
                      <span className="absolute -top-1.5 -right-2.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold text-on-primary tabular-nums">
                        {tab.badge > 99 ? "99+" : tab.badge}
                      </span>
                    )}
                  </span>
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
