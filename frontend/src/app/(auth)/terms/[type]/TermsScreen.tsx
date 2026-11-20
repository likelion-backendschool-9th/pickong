"use client";

import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { Badge, ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useTerms } from "./_api/termsApi";

/** 약관 전문: 회원가입 ‘보기’와 설정의 약관 및 동의 관리에서 엽니다. */
export function TermsScreen({ type }: { type: string }) {
  const { data, isPending, isError, error, refetch } = useTerms(type);

  return (
    <div className="min-h-dvh">
      <TopBar title={data?.title ?? "약관"} backHref={routes.signup()} />
      {isPending ? (
        <div className="space-y-3 px-5 pt-4">
          <Skeleton className="h-7 w-2/3" />
          <Skeleton className="h-40" />
        </div>
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <article className="px-5 pt-4 pb-12">
          <div className="flex items-center gap-2">
            <h2 className="text-[22px] font-bold">{data.title}</h2>
            <Badge tone={data.required ? "lime" : "neutral"}>{data.required ? "필수" : "선택"}</Badge>
          </div>
          <p className="mt-1.5 text-[13px] text-ink-faint">
            버전 {data.version} · {formatDotDate(data.effectiveDate)}부터 적용
          </p>
          <div className="mt-6 space-y-6">
            {data.sections.map((s) => (
              <section key={s.heading}>
                <h3 className="text-[15px] font-bold">{s.heading}</h3>
                <p className="mt-2 text-[14px] leading-relaxed whitespace-pre-line text-ink-sub">{s.body}</p>
              </section>
            ))}
          </div>
        </article>
      )}
    </div>
  );
}
