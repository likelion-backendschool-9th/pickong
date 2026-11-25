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
import { useAdminUsers, useLiftSanction, useSanctionUser, type AdminUser, type SanctionType } from "../_api/adminApi";
import { ReasonSheet } from "../_components/ReasonSheet";
import { SANCTION_TYPES, sanctionSummary } from "../_lib/labels";

const STATUS = { ACTIVE: { label: "정상", tone: "lime" }, SUSPENDED: { label: "정지", tone: "warning" }, BANNED: { label: "영구 정지", tone: "danger" } } as const;
type Filter = "ALL" | "REPORTED" | "SANCTIONED";

export function AdminUsersScreen() {
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const q = useDebounced(query.trim());
  const { data, isPending, isError, error, refetch } = useAdminUsers(q, filter);
  const sanction = useSanctionUser();
  const lift = useLiftSanction();
  const toast = useToast();
  const [target, setTarget] = useState<AdminUser | null>(null);
  const [type, setType] = useState<SanctionType>("WARNING");

  return (
    <div className="pb-10">
      <TopBar title="사용자 제재" backHref={routes.admin()} />
      <div className="space-y-3 px-5 pt-1 pb-2">
        <SearchInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="이름 · 아이디 검색" aria-label="사용자 검색" />
        <div className="flex gap-2">
          {(
            [
              ["ALL", "전체"],
              ["REPORTED", "신고 받음"],
              ["SANCTIONED", "제재 중"],
            ] as const
          ).map(([v, label]) => (
            <Chip key={v} selected={filter === v} onClick={() => setFilter(v)}>
              {label}
            </Chip>
          ))}
        </div>
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
                      {u.isCreator && <Badge tone="neutral">크리에이터</Badge>}
                    </span>
                    <span className="block truncate text-[12px] text-ink-sub">
                      @{u.username} · 게시물 {u.postCount} · 신고 {u.reportCount}
                    </span>
                  </span>
                </Link>
                {u.sanction ? (
                  <Button
                    size="sm"
                    variant="outline"
                    loading={lift.isPending && lift.variables === u.id}
                    onClick={() => lift.mutate(u.id, { onSuccess: () => toast({ message: `${u.nickname}님의 제재를 풀었어요` }) })}
                  >
                    해제
                  </Button>
                ) : (
                  <Button size="sm" variant="danger" onClick={() => setTarget(u)}>
                    제재
                  </Button>
                )}
              </div>
              {u.sanction && (
                <p className="mt-2 ml-[52px] rounded-lg bg-warning-soft px-3 py-2 text-[12px] text-warning">
                  {sanctionSummary(u.sanction)} · {u.sanction.reason}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      <ReasonSheet
        open={!!target}
        title={`${target?.nickname ?? ""}님 제재`}
        placeholder="제재 사유 (사용자에게 안내돼요)"
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
            <button key={t.value} type="button" role="radio" aria-checked={type === t.value} onClick={() => setType(t.value)} className="flex w-full items-center gap-3 py-2 text-left">
              <RadioMark checked={type === t.value} />
              <span>
                <span className="block text-[14px] font-bold">{t.label}</span>
                <span className="block text-[12px] text-ink-sub">{t.desc}</span>
              </span>
            </button>
          ))}
        </div>
      </ReasonSheet>
    </div>
  );
}
