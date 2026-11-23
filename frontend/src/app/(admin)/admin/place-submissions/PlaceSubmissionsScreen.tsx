"use client";

import { MapPinPlus } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMemo, useRef, useState } from "react";
import { ALL_CATEGORIES, categoryLabel, type PlaceCategory } from "@/entities/place";
import { usePlaceSubmissions } from "@/entities/place-submission";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatClock, formatDotDate } from "@/shared/lib/date";
import { ButtonLink } from "@/shared/ui/Button";
import { Chip, Tabs } from "@/shared/ui/controls";
import { EmptyState, ErrorState, Skeleton } from "@/shared/ui/display";
import { SearchInput } from "@/shared/ui/form";
import { TopBar } from "@/shared/ui/layout";
import { SubmissionStatusBadge } from "./_components/SubmissionStatusBadge";
import {
  countByStatus,
  filterSubmissions,
  parseSubmissionQuery,
  submissionSearchParams,
  type SubmissionQuery,
  type SubmissionStatusFilter,
} from "./_lib/filterSubmissions";

function formatWhen(iso: string) {
  return `${formatDotDate(iso)} ${formatClock(iso)}`;
}

const TABS: { value: SubmissionStatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "PENDING", label: "심사 대기" },
  { value: "APPROVED", label: "승인 완료" },
  { value: "REJECTED", label: "반려" },
];

export function PlaceSubmissionsScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const parsed = useMemo(() => parseSubmissionQuery(searchParams), [searchParams]);
  const [name, setName] = useState(parsed.name);
  const [submitter, setSubmitter] = useState(parsed.submitter);
  const timer = useRef<number | undefined>(undefined);
  const { data, isPending, isError, error, refetch } = usePlaceSubmissions();

  const replaceQuery = (patch: Partial<SubmissionQuery>, draft?: { name: string; submitter: string }) => {
    const params = new URLSearchParams(window.location.search);
    const qs = submissionSearchParams({ ...parseSubmissionQuery(params), name, submitter, ...draft, ...patch });
    if (qs === params.toString()) return;
    const path = routes.adminPlaceSubmissions();
    router.replace(qs ? `${path}?${qs}` : path, { scroll: false });
  };

  const scheduleText = (next: { name: string; submitter: string }) => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => replaceQuery({}, next), 300);
  };

  const counts = data ? countByStatus(data) : null;
  const filtered = data ? filterSubmissions(data, { ...parsed, name, submitter }) : [];
  const statValue = (value: number | undefined) => (isPending ? undefined : (value ?? null));

  return (
    <div className="pb-10">
      <TopBar title="제보 관리" backHref={routes.admin()} />
      <div className="px-5 pt-1">
        <p className="text-[13px] leading-relaxed text-ink-sub">사용자가 직접 등록한 장소를 검토하고 승인 또는 반려할 수 있습니다.</p>
        <div className="mt-4 grid grid-cols-2 gap-2">
          <Stat label="전체 제보" value={statValue(counts?.all)} selected={parsed.status === "ALL"} onClick={() => replaceQuery({ status: "ALL" })} />
          <Stat label="심사 대기" value={statValue(counts?.pending)} selected={parsed.status === "PENDING"} onClick={() => replaceQuery({ status: "PENDING" })} />
          <Stat label="승인 완료" value={statValue(counts?.approved)} selected={parsed.status === "APPROVED"} onClick={() => replaceQuery({ status: "APPROVED" })} />
          <Stat label="반려" value={statValue(counts?.rejected)} selected={parsed.status === "REJECTED"} onClick={() => replaceQuery({ status: "REJECTED" })} />
        </div>
      </div>

      <div className="scrollbar-none mt-4 overflow-x-auto border-b border-line px-5">
        <Tabs items={TABS} value={parsed.status} onChange={(status) => replaceQuery({ status })} />
      </div>

      <div className="space-y-2 px-5 pt-4">
        <SearchInput
          value={name}
          onChange={(e) => {
            const next = e.target.value;
            setName(next);
            scheduleText({ name: next, submitter });
          }}
          placeholder="장소명 검색"
          aria-label="장소명 검색"
        />
        <SearchInput
          value={submitter}
          onChange={(e) => {
            const next = e.target.value;
            setSubmitter(next);
            scheduleText({ name, submitter: next });
          }}
          placeholder="제보자 검색"
          aria-label="제보자 검색"
        />
        <div className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor="submission-category">
            카테고리
          </label>
          <select
            id="submission-category"
            value={parsed.category}
            onChange={(e) => replaceQuery({ category: e.target.value === "ALL" ? "ALL" : (e.target.value as PlaceCategory) })}
            className="h-10 min-w-[9.5rem] flex-1 rounded-control border border-line bg-bg px-3 text-[13px] text-ink"
          >
            <option value="ALL">전체 카테고리</option>
            {ALL_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </select>
          <div className="flex shrink-0 gap-1.5">
            <Chip selected={parsed.sort === "newest"} onClick={() => replaceQuery({ sort: "newest" })}>
              최신순
            </Chip>
            <Chip selected={parsed.sort === "oldest"} onClick={() => replaceQuery({ sort: "oldest" })}>
              오래된순
            </Chip>
          </div>
        </div>
      </div>

      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<MapPinPlus size={24} />}
          title={data.length === 0 ? "접수된 제보가 없어요" : "조건에 맞는 제보가 없어요"}
          description={data.length === 0 ? "사용자가 장소를 직접 등록하면 이곳에 표시됩니다." : "검색어나 필터를 바꿔 보세요."}
        />
      ) : (
        <ul className="space-y-3 px-5 pt-4">
          {filtered.map((item) => (
            <li key={item.id} className="rounded-card border border-line p-4">
              <div className="flex items-start gap-3">
                <div className="min-w-0 flex-1">
                  <p className="text-[12px] text-ink-faint tabular-nums">{item.id}</p>
                  <p className="mt-0.5 truncate text-[15px] font-bold">{item.name}</p>
                </div>
                <SubmissionStatusBadge status={item.status} />
              </div>
              <p className="mt-2 text-[13px] text-ink-sub">
                {categoryLabel(item.category)} · {item.address || "주소 없음"}
              </p>
              <p className="mt-1 text-[12px] text-ink-faint">
                {item.submitter.nickname} · {formatWhen(item.submittedAt)}
              </p>
              <ButtonLink href={routes.adminPlaceSubmission(item.id)} size="sm" variant="outline" className="mt-3">
                상세보기
              </ButtonLink>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Stat({ label, value, selected, onClick }: { label: string; value?: number | null; selected: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={cn("rounded-card border px-3 py-3 text-left", selected ? "border-primary-deep bg-soft" : "border-line bg-bg")}
    >
      <span className="block text-[12px] text-ink-sub">{label}</span>
      {value === undefined ? (
        <Skeleton className="mt-1 h-7 w-10" />
      ) : (
        <span className="mt-0.5 block text-[22px] font-bold tabular-nums">{value ?? "—"}</span>
      )}
    </button>
  );
}
