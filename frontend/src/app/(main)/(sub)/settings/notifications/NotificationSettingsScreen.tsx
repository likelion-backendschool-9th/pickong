"use client";

import { BellOff, ChevronRight } from "lucide-react";
import Link from "next/link";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { Switch } from "@/shared/ui/controls";
import { ErrorState, Notice, SectionTitle, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useNotificationSettings, useSaveNotificationSettings, type NotificationItem, type NotificationSettings } from "../_api/accountApi";

const GROUPS: { title: string; items: { key: NotificationItem; label: string; desc: string }[] }[] = [
  {
    title: "소셜",
    items: [
      { key: "like", label: "좋아요", desc: "내 게시물에 좋아요가 달리면" },
      { key: "comment", label: "댓글", desc: "내 게시물에 댓글 · 답글이 달리면" },
      { key: "follow", label: "새 팔로워", desc: "누군가 나를 팔로우하면" },
      { key: "message", label: "메시지", desc: "새 메시지 · 그룹 채팅" },
    ],
  },
  { title: "일정", items: [{ key: "planReminder", label: "일정 알림", desc: "방문 전날 저녁, 계획한 일정을 알려 드려요" }] },
  { title: "혜택", items: [{ key: "marketing", label: "이벤트 · 혜택", desc: "마케팅 정보 수신 동의와 같은 설정이에요" }] },
];

export function NotificationSettingsScreen() {
  const { data, isPending, isError, error, refetch } = useNotificationSettings();
  const save = useSaveNotificationSettings();

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />;

  const set = (next: Partial<NotificationSettings>) => save.mutate({ ...data, ...next });
  const off = !data.pushEnabled;

  return (
    <div className="pb-10">
      <TopBar title="푸시 알림 설정" backHref={routes.settings()} />
      <div className="px-5 pt-2">
        <div className="flex items-center gap-3 rounded-card bg-soft px-4 py-4">
          <div className="min-w-0 flex-1">
            <p className="text-[15px] font-bold">푸시 알림 받기</p>
            <p className="mt-0.5 text-[12px] text-ink-sub">끄면 아래 항목과 상관없이 푸시를 보내지 않아요</p>
          </div>
          <Switch label="푸시 알림 받기" hideLabel checked={data.pushEnabled} onChange={(pushEnabled) => set({ pushEnabled })} />
        </div>

        {GROUPS.map((g) => (
          <section key={g.title} className="mt-6">
            <SectionTitle className="mb-1">{g.title}</SectionTitle>
            <ul className={cn("divide-y divide-line", off && "opacity-50")}>
              {g.items.map((item) => (
                <li key={item.key} className="flex min-h-14 items-center gap-3 py-2.5">
                  <div className="min-w-0 flex-1">
                    <p className="text-[14px] font-bold">{item.label}</p>
                    <p className="text-[12px] text-ink-sub">{item.desc}</p>
                  </div>
                  <Switch
                    label={item.label}
                    hideLabel
                    checked={data.items[item.key]}
                    onChange={(v) => set({ items: { ...data.items, [item.key]: v } })}
                  />
                </li>
              ))}
            </ul>
          </section>
        ))}

        <section className={cn("mt-6", off && "opacity-50")}>
          <SectionTitle className="mb-1">방해 금지 시간</SectionTitle>
          <div className="flex min-h-14 items-center gap-3 py-2.5">
            <BellOff size={18} className="shrink-0 text-ink-sub" aria-hidden />
            <p className="min-w-0 flex-1 text-[14px]">정해 둔 시간에는 알림을 조용히 보관해요</p>
            <Switch
              label="방해 금지 시간"
              hideLabel
              checked={data.quietHours.enabled}
              onChange={(enabled) => set({ quietHours: { ...data.quietHours, enabled } })}
            />
          </div>
          {data.quietHours.enabled && (
            <div className="flex items-center gap-2 rounded-card bg-fill px-4 py-3 text-[14px]">
              <label className="sr-only" htmlFor="quiet-start">시작 시각</label>
              <input
                id="quiet-start"
                type="time"
                value={data.quietHours.start}
                onChange={(e) => e.target.value && set({ quietHours: { ...data.quietHours, start: e.target.value } })}
                className="rounded-md bg-bg px-2 py-1 tabular-nums"
              />
              <span className="text-ink-sub">부터</span>
              <label className="sr-only" htmlFor="quiet-end">끝 시각</label>
              <input
                id="quiet-end"
                type="time"
                value={data.quietHours.end}
                onChange={(e) => e.target.value && set({ quietHours: { ...data.quietHours, end: e.target.value } })}
                className="rounded-md bg-bg px-2 py-1 tabular-nums"
              />
              <span className="text-ink-sub">까지</span>
            </div>
          )}
        </section>

        {save.isError && <Notice tone="danger" className="mt-4">{errorMessage(save.error)}</Notice>}

        <Link href={routes.devices()} className="mt-8 flex items-center justify-between rounded-card border border-line px-4 py-3.5">
          <span>
            <span className="block text-[14px] font-bold">기기별 알림 관리</span>
            <span className="block text-[12px] text-ink-sub">로그인한 기기마다 푸시를 켜고 끌 수 있어요</span>
          </span>
          <ChevronRight size={18} className="text-ink-faint" />
        </Link>
      </div>
    </div>
  );
}
