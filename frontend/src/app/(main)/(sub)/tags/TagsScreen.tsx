"use client";

import { Hash } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { useDebounced } from "@/shared/lib/useDebounced";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useMyTags } from "./_api/tagApi";

/** 내 태그 검색: 검색어는 공유 가능하도록 URL(?q=)에 둡니다. */
export function TagsScreen({ initialQuery }: { initialQuery: string }) {
  const router = useRouter();
  const [query, setQuery] = useState(initialQuery);
  const debounced = useDebounced(query.trim().replace(/^#/, ""));
  const { data, isPending, isError, error, refetch } = useMyTags(debounced);

  useEffect(() => {
    router.replace(debounced ? `${routes.tags()}?q=${encodeURIComponent(debounced)}` : routes.tags(), { scroll: false });
  }, [debounced, router]);

  return (
    <div>
      <TopBar title="내 태그" backHref={routes.settings()} />
      <div className="px-5 pt-1 pb-3">
        <SearchInput value={query} onChange={(e) => setQuery(e.target.value)} placeholder="태그 검색 (예: 조용한)" aria-label="태그 검색" />
      </div>
      {isPending ? (
        <Skeleton className="mx-5 h-32" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState
          icon={<Hash size={24} />}
          title={debounced ? `‘${debounced}’ 태그가 없어요` : "아직 단 태그가 없어요"}
          description="장소 상세의 ‘내 태그’에서 나만의 분류를 달아 보세요."
        />
      ) : (
        <ul className="divide-y divide-line px-5">
          {data.map((t) => (
            <li key={t.name}>
              <Link href={routes.tag(t.name)} className="flex min-h-14 items-center gap-3 py-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-selected text-primary-deep">
                  <Hash size={16} aria-hidden />
                </span>
                <span className="min-w-0 flex-1 truncate text-[15px] font-bold">{t.name}</span>
                <span className="text-[13px] text-ink-sub">장소 {t.placeCount}곳</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
