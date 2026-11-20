"use client";

import Link from "next/link";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { visibilityLabel } from "@/shared/config/visibility";
import { cn } from "@/shared/lib/cn";
import { formatDotDate } from "@/shared/lib/date";
import { useDebounced } from "@/shared/lib/useDebounced";
import { formatWon } from "@/shared/lib/money";
import { Switch, Tabs } from "@/shared/ui/controls";
import { Badge, EmptyState, ErrorState, Skeleton, Thumb } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useAdminContents, useSetContentHidden, type AdminContent } from "../_api/adminApi";

/** 게시물 · 컬렉션 숨김 관리. 숨기면 작성자 외에는 볼 수 없어요. */
export function ContentsScreen() {
  const [type, setType] = useState<AdminContent["type"]>("POST");
  const [query, setQuery] = useState("");
  const q = useDebounced(query.trim());
  const { data, isPending, isError, error, refetch } = useAdminContents(type, q);
  const setHidden = useSetContentHidden();
  const toast = useToast();

  return (
    <div className="pb-10">
      <TopBar title="콘텐츠 관리" backHref={routes.admin()} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "POST", label: "게시물" },
            { value: "COLLECTION", label: "컬렉션" },
          ]}
          value={type}
          onChange={setType}
        />
      </div>
      <div className="px-5 pt-3 pb-1">
        <SearchInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="제목 · 작성자 검색" aria-label="콘텐츠 검색" />
      </div>
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title="해당하는 콘텐츠가 없어요" />
      ) : (
        <ul className="divide-y divide-line px-5">
          {data.map((c) => (
            <li key={c.id} className={cn("flex items-center gap-3 py-3", c.hidden && "opacity-60")}>
              <Link href={c.link} target="_blank" className="flex min-w-0 flex-1 items-center gap-3">
                <Thumb src={c.imageUrl} alt="" className="size-14" />
                <span className="min-w-0">
                  <span className="block truncate text-[14px] font-bold">{c.title}</span>
                  <span className="block truncate text-[12px] text-ink-sub">
                    {c.author?.nickname ?? "알 수 없음"} · {visibilityLabel(c.visibility)} · {formatDotDate(c.createdAt)}
                  </span>
                  <span className="mt-1 flex gap-1">
                    {c.hidden && <Badge tone="neutral">숨김</Badge>}
                    {c.reportCount > 0 && <Badge tone="danger">신고 {c.reportCount}</Badge>}
                    {c.price && <Badge>{formatWon(c.price)}</Badge>}
                  </span>
                </span>
              </Link>
              <Switch
                label="숨김"
                checked={c.hidden}
                onChange={(hidden) =>
                  setHidden.mutate(
                    { type: c.type, id: c.id, hidden },
                    { onSuccess: () => toast({ message: hidden ? "콘텐츠를 숨겼어요" : "콘텐츠를 다시 보이게 했어요" }) },
                  )
                }
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
