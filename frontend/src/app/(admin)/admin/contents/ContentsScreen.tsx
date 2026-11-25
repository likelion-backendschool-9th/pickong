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
import { useAdminContents, useSetContentHidden } from "../_api/adminApi";
import type { AdminContent } from "../_lib/models";
import { useAdminSearch } from "../_lib/useAdminSearch";
import { ReasonSheet } from "../_components/ReasonSheet";

/** 게시물 · 컬렉션 숨김 관리. 숨기면 작성자 외에는 볼 수 없어요. */
export function ContentsScreen() {
  const { params, setParam } = useAdminSearch();
  const type = params.get("type") === "COLLECTION" ? "COLLECTION" : "POST";
  const query = params.get("q") ?? "";
  const q = useDebounced(query.trim());
  const { data, isPending, isError, error, refetch } = useAdminContents(type, q);
  const setHidden = useSetContentHidden();
  const toast = useToast();
  const [target, setTarget] = useState<{ content: AdminContent; hidden: boolean } | null>(null);

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
          onChange={(value) => setParam("type", value, "POST")}
        />
      </div>
      <div className="px-5 pt-3 pb-1">
        <SearchInput value={query} onChange={(e) => setParam("q", e.target.value)} disabled={type === "COLLECTION"} placeholder="제목 · 작성자 검색" aria-label="콘텐츠 검색" />
      </div>
      {type === "COLLECTION" ? (
        <EmptyState title="컬렉션 관리 연동 준비 중이에요" description="컬렉션 서비스가 준비되면 조회와 숨김 관리를 연결할 예정이에요." />
      ) : isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : !data || data.length === 0 ? (
        <EmptyState title="해당하는 콘텐츠가 없어요" />
      ) : (
        <ul className="divide-y divide-line px-5">
          {data.map((c) => (
            <li key={c.id} className={cn("flex items-center gap-3 py-3", c.hidden && "opacity-60")}>
              {c.link ? (
                <Link href={c.link} target="_blank" className="flex min-w-0 flex-1 items-center gap-3"><ContentPreview content={c} /></Link>
              ) : (
                <div className="flex min-w-0 flex-1 items-center gap-3"><ContentPreview content={c} /></div>
              )}
              <Switch
                label={`${c.title || "게시물"} 숨김`}
                hideLabel
                checked={c.hidden}
                disabled={setHidden.isPending || q !== query.trim()}
                onChange={(hidden) => { setHidden.reset(); setTarget({ content: c, hidden }); }}
              />
            </li>
          ))}
        </ul>
      )}
      <ReasonSheet
        open={!!target}
        title={target?.hidden ? "게시물 숨김" : "게시물 복구"}
        description={target?.hidden ? "작성자와 관리자만 조회할 수 있어요. 기존 공개 범위와 반응은 유지해요." : "관리자 숨김을 해제해요. 기존 공개 범위를 그대로 유지해요."}
        placeholder={target?.hidden ? "숨김 사유 (필수)" : "복구 사유 (선택)"}
        required={target?.hidden ?? true}
        confirmLabel={target?.hidden ? "숨김 적용" : "복구"}
        loading={setHidden.isPending}
        error={setHidden.isError ? setHidden.error : null}
        onClose={() => { setTarget(null); setHidden.reset(); }}
        onConfirm={(reason) => target && setHidden.mutate(
          { id: target.content.id, hidden: target.hidden, reason },
          { onSuccess: () => { toast({ message: target.hidden ? "게시물을 숨겼어요" : "게시물을 복구했어요" }); setTarget(null); } },
        )}
      />
    </div>
  );
}

function ContentPreview({ content: c }: { content: AdminContent }) {
  return <>
    <Thumb src={c.imageUrl} alt="" className="size-14" />
    <span className="min-w-0">
      <span className="block truncate text-[14px] font-bold">{c.title || "내용 없는 게시물"}</span>
      <span className="block truncate text-[12px] text-ink-sub">
        {c.author?.nickname ?? "알 수 없음"} · {visibilityLabel(c.visibility)} · {formatDotDate(c.createdAt)}
      </span>
      <span className="mt-1 flex gap-1">
        {c.hidden && <Badge tone="neutral">숨김</Badge>}
        {c.reportCount > 0 && <Badge tone="danger">신고 {c.reportCount}</Badge>}
        {c.price != null && c.price > 0 && <Badge>{formatWon(c.price)}</Badge>}
      </span>
    </span>
  </>;
}
