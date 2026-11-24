"use client";

import { useState, type ReactNode } from "react";
import { categoryLabel } from "@/entities/place";
import { useApprovePlaceSubmission, usePlaceSubmission, useRejectPlaceSubmission } from "@/entities/place-submission";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatClock, formatDotDate } from "@/shared/lib/date";
import { MapView } from "@/shared/map";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { ErrorState, Notice, Photo, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { ReasonSheet } from "../../_components/ReasonSheet";
import { SubmissionStatusBadge } from "../_components/SubmissionStatusBadge";

function formatWhen(iso: string) {
  return `${formatDotDate(iso)} ${formatClock(iso)}`;
}

export function PlaceSubmissionDetailScreen({ id }: { id: string }) {
  const { data, isPending, isError, error, refetch } = usePlaceSubmission(id);
  const approve = useApprovePlaceSubmission();
  const reject = useRejectPlaceSubmission();
  const toast = useToast();
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const busy = approve.isPending || reject.isPending;

  return (
    <div className="flex min-h-dvh flex-col pb-6">
      <TopBar title="제보 상세" backHref={routes.adminPlaceSubmissions()} />
      {isPending ? (
        <Skeleton className="m-5 h-64" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <>
          <div className="px-5 pt-2">
            <div className="flex items-start gap-3">
              <h2 className="min-w-0 flex-1 text-[20px] font-bold leading-snug">{data.name}</h2>
              <SubmissionStatusBadge status={data.status} />
            </div>
            <p className="mt-1 text-[12px] text-ink-faint tabular-nums">{data.id}</p>

            {data.imageUrl && (
              <div className="relative mt-4 h-48 overflow-hidden rounded-card bg-fill">
                <Photo src={data.imageUrl} alt={`${data.name} 제보 사진`} />
              </div>
            )}

            <dl className="mt-2">
              <Fact label="카테고리">{categoryLabel(data.category)}</Fact>
              <Fact label="주소">{data.address || "주소 없음"}</Fact>
              <Fact label="위도 · 경도">
                {data.location ? (
                  <span className="tabular-nums">
                    {data.location.lat.toFixed(5)}, {data.location.lng.toFixed(5)}
                  </span>
                ) : (
                  "좌표를 확인할 수 없어요"
                )}
              </Fact>
              <Fact label="설명">{data.description || "설명이 없어요"}</Fact>
              <Fact label="제보자">
                {data.submitter.nickname}
                {data.submitter.username ? <span className="text-ink-sub"> @{data.submitter.username}</span> : null}
              </Fact>
              <Fact label="제보 일시">{formatWhen(data.submittedAt)}</Fact>
              <Fact label="심사 상태">
                <SubmissionStatusBadge status={data.status} />
              </Fact>
              {data.reviewedAt && <Fact label="심사 일시">{formatWhen(data.reviewedAt)}</Fact>}
              {data.status === "REJECTED" && <Fact label="반려 사유">{data.rejectReason || "사유가 없습니다."}</Fact>}
            </dl>

            <div className="mt-5">
              <p className="mb-2 text-[13px] font-bold">위치 미리보기</p>
              {data.location ? (
                <MapView
                  className="h-56 rounded-card"
                  center={data.location}
                  metersPerPixel={2.4}
                  markers={[{ id: data.id, position: data.location, title: data.name }]}
                  selectedId={data.id}
                  caption="제보된 위치"
                  ariaLabel="제보 장소 위치"
                />
              ) : (
                <Notice>좌표가 없거나 올바르지 않아 지도를 표시할 수 없어요.</Notice>
              )}
            </div>
          </div>

          {data.status === "PENDING" && (
            <div className="sticky bottom-0 z-20 mt-auto grid grid-cols-2 gap-2 border-t border-line bg-bg/95 px-5 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] backdrop-blur">
              <Button variant="outline" disabled={busy} onClick={() => setRejectOpen(true)}>
                반려
              </Button>
              <Button disabled={busy} onClick={() => setApproveOpen(true)}>
                승인
              </Button>
            </div>
          )}
        </>
      )}

      <BottomSheet
        open={approveOpen}
        onClose={() => {
          if (approve.isPending) return;
          setApproveOpen(false);
          approve.reset();
        }}
        title="이 제보를 승인할까요?"
        description="승인하면 이 장소가 공개 지도에 표시됩니다."
        footer={
          <div className="grid grid-cols-2 gap-2">
            <Button
              variant="outline"
              block
              disabled={approve.isPending}
              onClick={() => {
                setApproveOpen(false);
                approve.reset();
              }}
            >
              취소
            </Button>
            <Button
              block
              loading={approve.isPending}
              onClick={() =>
                approve.mutate(id, {
                  onSuccess: () => {
                    setApproveOpen(false);
                    toast({ message: "장소를 승인했어요", detail: "공개 지도에 표시됩니다." });
                  },
                })
              }
            >
              승인
            </Button>
          </div>
        }
      >
        {approve.isError && <Notice tone="danger">{errorMessage(approve.error)}</Notice>}
      </BottomSheet>

      <ReasonSheet
        open={rejectOpen}
        title="제보 반려"
        description="반려 사유는 필수입니다. 사유는 제보 기록에 남습니다."
        placeholder="반려 사유를 입력해 주세요"
        confirmLabel="반려"
        danger
        loading={reject.isPending}
        error={reject.isError ? reject.error : null}
        onClose={() => {
          if (reject.isPending) return;
          setRejectOpen(false);
          reject.reset();
        }}
        onConfirm={(reason) =>
          reject.mutate(
            { id, reason },
            {
              onSuccess: () => {
                setRejectOpen(false);
                toast({ message: "제보를 반려했어요" });
              },
            },
          )
        }
      />
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-b border-line py-3">
      <dt className="text-[12px] font-bold text-ink-faint">{label}</dt>
      <dd className="mt-0.5 text-[14px] leading-relaxed">{children}</dd>
    </div>
  );
}
