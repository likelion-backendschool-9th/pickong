"use client";

import { Map } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { ALL_CATEGORIES, categoryLabel, type PlaceCategory } from "@/entities/place";
import { routes } from "@/shared/config/routes";
import { Button, ButtonLink } from "@/shared/ui/Button";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { useAdminPlaces } from "../_api/queries";
import {
  adminPlaceSearchParams,
  filterAdminPlaces,
  hasAdminPlaceFilter,
  parseAdminPlaceQuery,
  type AdminPlaceListQuery,
  type AdminPlaceSourceFilter,
} from "../_lib/filterAdminPlaces";
import { AdminPlaceCard } from "./AdminPlaceCard";

/** 지도 관리 목록의 뒤로가기는 방문 기록과 관계없이 관리자 메인으로 갑니다. */
export function leaveAdminPlaces(push: (path: string) => void) {
  push(routes.admin());
}

const SOURCE_OPTIONS: { value: AdminPlaceSourceFilter; label: string }[] = [
  { value: "ALL", label: "전체 출처" },
  { value: "SYSTEM", label: "시스템" },
  { value: "CUSTOM", label: "사용자 등록" },
  { value: "AI_EXTRACTED", label: "AI 추출" },
  { value: "OTHER", label: "기타 출처" },
];

export function AdminPlacesScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const parsed = useMemo(() => parseAdminPlaceQuery(searchParams), [searchParams]);
  const [name, setName] = useState(parsed.q);
  const written = useRef(parsed.q);
  const timer = useRef<number | undefined>(undefined);
  const { data, isPending, isError, refetch } = useAdminPlaces();

  useEffect(() => {
    if (parsed.q === written.current) return;
    written.current = parsed.q;
    setName(parsed.q);
  }, [parsed.q]);

  const replaceQuery = (patch: Partial<AdminPlaceListQuery>, draftName = name) => {
    const params = new URLSearchParams(window.location.search);
    const next = { ...parseAdminPlaceQuery(params), q: draftName, ...patch };
    const qs = adminPlaceSearchParams(next);
    if (qs === params.toString()) return;
    written.current = next.q.trim();
    const path = routes.adminPlaces();
    router.replace(qs ? `${path}?${qs}` : path, { scroll: false });
  };

  const scheduleName = (next: string) => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => replaceQuery({}, next), 300);
  };

  const reset = () => {
    window.clearTimeout(timer.current);
    setName("");
    written.current = "";
    replaceQuery({ q: "", category: "ALL", source: "ALL" }, "");
  };

  const query = { ...parsed, q: name };
  const filtered = data ? filterAdminPlaces(data, query) : [];
  const filtering = hasAdminPlaceFilter(query);
  const listQuery = adminPlaceSearchParams(parsed);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  return (
    <div className="pb-10">
      <TopBar title="지도 관리" onBack={() => leaveAdminPlaces(router.push)} />
      <div className="flex items-start justify-between gap-3 px-5 pt-1">
        <p className="min-w-0 text-[13px] leading-relaxed text-ink-sub">공개 지도에 제공되는 장소를 조회하고 관리할 수 있어요.</p>
        <ButtonLink href={routes.adminNewPlace()} size="md" className="shrink-0">
          장소 추가
        </ButtonLink>
      </div>

      <div className="space-y-2 px-5 pt-4">
        <SearchInput
          value={name}
          onChange={(event) => {
            const next = event.target.value;
            setName(next);
            scheduleName(next);
          }}
          placeholder="이름 또는 주소 검색"
          aria-label="장소 이름 또는 주소 검색"
        />
        <div className="flex flex-wrap gap-2">
          <label className="sr-only" htmlFor="admin-place-category">
            카테고리
          </label>
          <select
            id="admin-place-category"
            value={parsed.category}
            onChange={(event) => replaceQuery({ category: event.target.value === "ALL" ? "ALL" : (event.target.value as PlaceCategory) })}
            className="h-11 min-w-0 flex-1 rounded-control border border-line bg-bg px-3 text-[13px] text-ink"
          >
            <option value="ALL">전체 카테고리</option>
            {ALL_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </select>
          <label className="sr-only" htmlFor="admin-place-source">
            출처
          </label>
          <select
            id="admin-place-source"
            value={parsed.source}
            onChange={(event) => replaceQuery({ source: event.target.value as AdminPlaceSourceFilter })}
            className="h-11 min-w-0 flex-1 rounded-control border border-line bg-bg px-3 text-[13px] text-ink"
          >
            {SOURCE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center justify-between gap-3">
          {data ? (
            <p className="text-[12px] text-ink-sub tabular-nums">{filtering ? `검색 결과 ${filtered.length}곳` : `전체 ${data.length}곳`}</p>
          ) : (
            <Skeleton className="h-4 w-16" />
          )}
          {filtering && (
            <button type="button" onClick={reset} className="h-9 rounded-control px-2 text-[13px] font-medium text-primary-deep">
              초기화
            </button>
          )}
        </div>
      </div>

      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError && !data ? (
        <ErrorState message="장소 목록을 불러오지 못했어요." onRetry={() => refetch()} />
      ) : data && data.length === 0 ? (
        <EmptyState icon={<Map size={24} />} title="등록된 장소가 없어요." description="관리자가 장소를 등록하면 이곳에 표시됩니다." />
      ) : filtered.length === 0 ? (
        <EmptyState icon={<Map size={24} />} title="검색 조건에 맞는 장소가 없어요." description="검색어나 필터를 바꿔 보세요.">
          <Button variant="outline" size="md" onClick={reset}>
            검색 조건 초기화
          </Button>
        </EmptyState>
      ) : (
        <>
          {isError && data && (
            <div className="mx-5 mt-4 flex items-center justify-between gap-3 rounded-card bg-soft px-4 py-3">
              <p className="text-[13px] text-ink-sub">최신 목록을 불러오지 못했어요.</p>
              <button type="button" onClick={() => refetch()} className="h-9 shrink-0 rounded-control border border-line px-3 text-[13px] font-medium">
                다시 시도
              </button>
            </div>
          )}
          <ul className="space-y-3 px-5 pt-4">
            {filtered.map((place) => (
              <li key={place.id}>
                <AdminPlaceCard place={place} listQuery={listQuery} blocked={deletingId != null && deletingId !== place.id} onPending={setDeletingId} />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
