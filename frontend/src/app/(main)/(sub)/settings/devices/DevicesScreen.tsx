"use client";

import { Globe, Smartphone, Tablet } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Switch } from "@/shared/ui/controls";
import { Badge, EmptyState, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useDevices, useRemoveDevice, useUpdateDevice, type Device } from "../_api/accountApi";

const PLATFORM: Record<Device["platform"], { label: string; icon: typeof Smartphone }> = {
  IOS: { label: "iOS 앱", icon: Smartphone },
  ANDROID: { label: "Android 앱", icon: Tablet },
  WEB: { label: "웹 브라우저", icon: Globe },
};

/** 로그인한 기기 목록: 기기별 푸시 켬/끔, 다른 기기 로그아웃(등록 해제) */
export function DevicesScreen() {
  const { data, isPending, isError, error, refetch } = useDevices();
  const update = useUpdateDevice();
  const remove = useRemoveDevice();
  const toast = useToast();
  const [target, setTarget] = useState<Device | null>(null);

  return (
    <div>
      <TopBar title="디바이스 관리" backHref={routes.settings()} />
      <p className="px-5 pt-1 pb-3 text-[13px] text-ink-sub">찜콩에 로그인한 기기예요. 모르는 기기가 있으면 로그아웃해 주세요.</p>
      {isPending ? (
        <Skeleton className="mx-5 h-40" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title="로그인한 기기가 없어요" />
      ) : (
        <ul className="divide-y divide-line px-5">
          {data.map((d) => {
            const { label, icon: Icon } = PLATFORM[d.platform];
            return (
              <li key={d.id} className="py-4">
                <div className="flex items-start gap-3">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-soft text-primary-deep">
                    <Icon size={19} aria-hidden />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate text-[15px] font-bold">{d.name}</span>
                      {d.isCurrent && <Badge>이 기기</Badge>}
                    </div>
                    <p className="mt-0.5 text-[12px] text-ink-sub">
                      {label} · {d.isCurrent ? "지금 사용 중" : `${formatRelative(d.lastActiveAt)} 접속`}
                    </p>
                  </div>
                  <Switch
                    label="푸시"
                    checked={d.pushEnabled}
                    onChange={(pushEnabled) => update.mutate({ id: d.id, pushEnabled })}
                  />
                </div>
                {!d.isCurrent && (
                  <button type="button" onClick={() => setTarget(d)} className="mt-2 ml-[52px] text-[13px] font-medium text-danger">
                    이 기기에서 로그아웃
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {update.isError && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(update.error)}</Notice>}

      <BottomSheet open={!!target} onClose={() => setTarget(null)} title="기기에서 로그아웃할까요?">
        {target && (
          <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
            <p className="text-[14px] text-ink-sub">
              <b className="text-ink">{target.name}</b>에서 로그아웃되고, 이 기기로는 더 이상 푸시 알림을 보내지 않아요.
            </p>
            {remove.isError && <Notice tone="danger" className="mt-3">{errorMessage(remove.error)}</Notice>}
            <div className="mt-5 flex gap-2">
              <Button variant="outline" block onClick={() => setTarget(null)}>
                취소
              </Button>
              <Button
                variant="danger"
                block
                loading={remove.isPending}
                onClick={() =>
                  remove.mutate(target.id, {
                    onSuccess: () => {
                      toast({ message: `${target.name}에서 로그아웃했어요` });
                      setTarget(null);
                    },
                  })
                }
              >
                로그아웃
              </Button>
            </div>
          </div>
        )}
      </BottomSheet>
    </div>
  );
}
