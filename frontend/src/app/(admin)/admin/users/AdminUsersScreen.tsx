"use client";

import Link from "next/link";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { useDebounced } from "@/shared/lib/useDebounced";
import { Button } from "@/shared/ui/Button";
import { Chip, RadioMark } from "@/shared/ui/controls";
import { Avatar, Badge, EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useAdminUsers, useLiftSanction, useSanctionUser } from "../_api/adminApi";
import type { AdminUser, AdminUserFilter, Sanction, SanctionType } from "../_lib/models";
import { useAdminSearch } from "../_lib/useAdminSearch";
import { ReasonSheet } from "../_components/ReasonSheet";
import { SANCTION_TYPES, sanctionSummary } from "../_lib/labels";
import { canSanction } from "./_lib/sanctionPolicy";

const STATUS = {
  ACTIVE: { label: "정상", tone: "lime" }, SUSPENDED: { label: "정지", tone: "warning" }, BANNED: { label: "영구 정지", tone: "danger" },
  WITHDRAWN_PENDING: { label: "탈퇴 진행 중", tone: "neutral" }, WITHDRAWN: { label: "탈퇴 완료", tone: "neutral" },
} as const;

export function AdminUsersScreen() {
  const { params, setParam } = useAdminSearch();
  const query = params.get("q") ?? "";
  const rawFilter = params.get("filter");
  const filter: AdminUserFilter = rawFilter === "REPORTED" || rawFilter === "SANCTIONED" ? rawFilter : "ALL";
  const q = useDebounced(query.trim());
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminUsers(q, filter);
  const sanction = useSanctionUser();
  const lift = useLiftSanction();
  const toast = useToast();
  const [target, setTarget] = useState<AdminUser | null>(null);
  const [type, setType] = useState<SanctionType>("WARNING");
  const [revokeTarget, setRevokeTarget] = useState<{ user: AdminUser; sanction: Sanction } | null>(null);
  const busy = sanction.isPending || lift.isPending || isPlaceholderData || q !== query.trim();

  return (
    <div className="pb-10">
      <TopBar title="사용자 제재" backHref={routes.admin()} />
      <div className="space-y-3 px-5 pt-1 pb-2">
        <SearchInput value={query} onChange={(e) => setParam("q", e.target.value)} placeholder="이름 · 아이디 검색" aria-label="사용자 검색" />
        <div className="flex gap-2">
          {(
            [
              ["ALL", "전체"],
              ["REPORTED", "신고 받음"],
              ["SANCTIONED", "제재 중"],
            ] as const
          ).map(([v, label]) => (
            <Chip key={v} selected={filter === v} onClick={() => setParam("filter", v, "ALL")}>
              {label}
            </Chip>
          ))}
        </div>
        <p className="text-[12px] text-ink-sub">신고 받음은 전체 신고 이력, 제재 중은 경고를 포함한 유효한 제재 기준이에요.</p>
      </div>
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title="해당하는 사용자가 없어요" />
      ) : (
        <ul className="divide-y divide-line px-5">
          {data.map((u) => (
            <li key={u.id} className="py-3.5">
              <div className="flex items-center gap-3">
                <Link href={routes.user(u.username)} target="_blank" className="flex min-w-0 flex-1 items-center gap-3">
                  <Avatar src={u.profileImageUrl} name={u.nickname} size={40} />
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5">
                      <span className="truncate text-[14px] font-bold">{u.nickname}</span>
                      <Badge tone={STATUS[u.status].tone}>{STATUS[u.status].label}</Badge>
                      {u.isCreator === true && <Badge tone="neutral">크리에이터</Badge>}
                    </span>
                    <span className="block truncate text-[12px] text-ink-sub">
                      @{u.username} · 게시물 {u.postCount} · 신고 {u.reportCount}
                      {u.isCreator === null && " · 크리에이터 여부 확인 불가"}
                    </span>
                  </span>
                </Link>
                <Button size="sm" variant="danger" disabled={busy || !canSanction(u.status, "WARNING")}
                  onClick={() => { sanction.reset(); setType("WARNING"); setTarget(u); }}>
                  제재
                </Button>
              </div>
              {u.sanctions.map((active) => (
                <div key={active.id} className="mt-2 ml-[52px] flex items-center gap-2 rounded-lg bg-warning-soft px-3 py-2">
                  <p className="min-w-0 flex-1 break-words text-[12px] text-warning">{sanctionSummary(active)} · {active.reason}</p>
                  <Button size="sm" variant="outline" disabled={busy} aria-label={`${u.nickname} ${sanctionSummary(active)} 해제`}
                    onClick={() => { lift.reset(); setRevokeTarget({ user: u, sanction: active }); }}>해제</Button>
                </div>
              ))}
            </li>
          ))}
        </ul>
      )}

      <ReasonSheet
        open={!!target}
        title={`${target?.nickname ?? ""}님 제재`}
        placeholder="제재 사유 (필수)"
        description="제재 사유를 이력에 저장해요. 경고는 이용을 제한하지 않아요."
        confirmLabel="제재 적용"
        danger
        loading={sanction.isPending}
        error={sanction.isError ? sanction.error : null}
        onClose={() => {
          setTarget(null);
          sanction.reset();
        }}
        onConfirm={(reason) =>
          target &&
          sanction.mutate(
            { id: target.id, type, reason },
            {
              onSuccess: () => {
                toast({ message: `${target.nickname}님에게 제재를 적용했어요` });
                setTarget(null);
              },
            },
          )
        }
      >
        <div className="mb-3 space-y-1" role="radiogroup" aria-label="제재 종류">
          {SANCTION_TYPES.map((t) => (
            <button key={t.value} type="button" role="radio" disabled={!target || !canSanction(target.status, t.value, target.sanctions.find((s) => s.type !== "WARNING")?.type ?? null)} aria-checked={type === t.value} onClick={() => setType(t.value)} className="flex w-full items-center gap-3 py-2 text-left disabled:opacity-40">
              <RadioMark checked={type === t.value} />
              <span>
                <span className="block text-[14px] font-bold">{t.label}</span>
                <span className="block text-[12px] text-ink-sub">{t.desc}</span>
              </span>
            </button>
          ))}
        </div>
      </ReasonSheet>
      <ReasonSheet
        open={!!revokeTarget}
        title={`${revokeTarget?.user.nickname ?? ""}님 제재 해제`}
        description={revokeTarget ? `${sanctionSummary(revokeTarget.sanction)} 이력만 해제해요. 다른 유효한 제재는 유지돼요.` : ""}
        placeholder="해제 사유 (선택)"
        required={false}
        confirmLabel="해제"
        loading={lift.isPending}
        error={lift.isError ? lift.error : null}
        onClose={() => { setRevokeTarget(null); lift.reset(); }}
        onConfirm={(reason) => revokeTarget && lift.mutate(
          { userId: revokeTarget.user.id, sanctionId: revokeTarget.sanction.id, reason },
          { onSuccess: () => { toast({ message: "선택한 제재를 해제했어요" }); setRevokeTarget(null); } },
        )}
      />
    </div>
  );
}
