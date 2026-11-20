"use client";

import { ExternalLink } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatRelative } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { Chip, Tabs } from "@/shared/ui/controls";
import { Badge, EmptyState, ErrorState, Skeleton, Thumb } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useReports, useResolveReport, type Report, type ReportAction, type SanctionType } from "../_api/adminApi";
import { ReasonSheet } from "../_components/ReasonSheet";
import { REPORT_ACTION, REPORT_TARGET, SANCTION_TYPES, reportReasonLabel } from "../_lib/labels";

export function ReportsScreen() {
  const [status, setStatus] = useState<Report["status"]>("PENDING");
  const { data, isPending, isError, error, refetch } = useReports(status);
  const resolve = useResolveReport();
  const toast = useToast();
  const [target, setTarget] = useState<{ report: Report; action: ReportAction } | null>(null);
  const [sanctionType, setSanctionType] = useState<SanctionType>("WARNING");

  return (
    <div className="pb-10">
      <TopBar title="신고 처리" backHref={routes.admin()} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "PENDING", label: "대기" },
            { value: "RESOLVED", label: "처리 완료" },
          ]}
          value={status}
          onChange={setStatus}
        />
      </div>
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : data.length === 0 ? (
        <EmptyState title={status === "PENDING" ? "처리할 신고가 없어요" : "처리한 신고가 없어요"} />
      ) : (
        <ul className="space-y-3 px-5 pt-4">
          {data.map((r) => (
            <li key={r.id} className="rounded-card border border-line p-4">
              <div className="flex items-center gap-1.5">
                <Badge tone="neutral">{REPORT_TARGET[r.targetType]}</Badge>
                <Badge tone="danger">{reportReasonLabel(r.reason)}</Badge>
                <span className="ml-auto text-[11px] text-ink-faint">{formatRelative(r.createdAt)}</span>
              </div>
              <div className="mt-3 flex gap-3">
                {r.target.imageUrl && <Thumb src={r.target.imageUrl} alt="" className="size-14" />}
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-2 text-[14px] font-bold">{r.target.text}</p>
                  <p className="mt-0.5 text-[12px] text-ink-sub">
                    작성자 {r.reportedUser?.nickname ?? "알 수 없음"} · 신고자 {r.reporter?.nickname ?? "알 수 없음"}
                  </p>
                </div>
                {r.target.link && (
                  <Link href={r.target.link} target="_blank" aria-label="신고 대상 열기" className="shrink-0 text-ink-faint">
                    <ExternalLink size={16} />
                  </Link>
                )}
              </div>
              {r.detail && <p className="mt-2 rounded-lg bg-fill px-3 py-2 text-[13px] text-ink-sub">“{r.detail}”</p>}

              {r.status === "PENDING" ? (
                <div className="mt-3 grid grid-cols-3 gap-2">
                  <Button size="sm" variant="outline" onClick={() => setTarget({ report: r, action: "DISMISS" })}>
                    기각
                  </Button>
                  <Button size="sm" variant="secondary" disabled={r.targetType === "USER"} onClick={() => setTarget({ report: r, action: "HIDE_CONTENT" })}>
                    숨김
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => setTarget({ report: r, action: "SANCTION_USER" })}>
                    제재
                  </Button>
                </div>
              ) : (
                r.resolution && (
                  <p className="mt-3 text-[12px] text-ink-sub">
                    <b className="text-ink">{REPORT_ACTION[r.resolution.action]}</b>
                    {r.resolution.memo && ` · ${r.resolution.memo}`} · {formatRelative(r.resolution.resolvedAt)}
                  </p>
                )
              )}
            </li>
          ))}
        </ul>
      )}

      <ReasonSheet
        open={!!target}
        title={target ? `신고 ${REPORT_ACTION[target.action]}` : ""}
        description={
          target?.action === "DISMISS"
            ? "문제가 없다고 판단해 신고를 닫아요. 같은 대상의 다른 신고도 함께 닫혀요."
            : target?.action === "HIDE_CONTENT"
              ? "콘텐츠를 다른 사람에게 보이지 않게 숨겨요. 콘텐츠 관리에서 다시 보이게 할 수 있어요."
              : `${target?.report.reportedUser?.nickname ?? "작성자"}님에게 제재를 적용해요.`
        }
        placeholder={target?.action === "DISMISS" ? "처리 메모 (선택)" : "처리 사유 (사용자에게 안내돼요)"}
        required={target?.action !== "DISMISS"}
        confirmLabel={target ? REPORT_ACTION[target.action] : ""}
        danger={target?.action === "SANCTION_USER"}
        loading={resolve.isPending}
        error={resolve.isError ? resolve.error : null}
        onClose={() => {
          setTarget(null);
          resolve.reset();
        }}
        onConfirm={(memo) =>
          target &&
          resolve.mutate(
            { id: target.report.id, action: target.action, memo, sanctionType: target.action === "SANCTION_USER" ? sanctionType : undefined },
            {
              onSuccess: () => {
                toast({ message: `신고를 ${REPORT_ACTION[target.action]} 처리했어요` });
                setTarget(null);
              },
            },
          )
        }
      >
        {target?.action === "SANCTION_USER" && (
          <div className="mb-3 flex flex-wrap gap-2">
            {SANCTION_TYPES.map((t) => (
              <Chip key={t.value} selected={sanctionType === t.value} onClick={() => setSanctionType(t.value)}>
                {t.label}
              </Chip>
            ))}
          </div>
        )}
      </ReasonSheet>
    </div>
  );
}
