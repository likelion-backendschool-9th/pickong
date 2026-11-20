"use client";

import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { Button } from "@/shared/ui/Button";
import { Tabs } from "@/shared/ui/controls";
import { Avatar, Badge, EmptyState, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useCreatorApplications, useReviewCreator, type CreatorApplicationReview } from "../_api/adminApi";
import { ReasonSheet } from "../_components/ReasonSheet";

const CATEGORY: Record<string, string> = { CAFE: "카페", FOOD: "맛집", TRAVEL: "여행 · 나들이", CULTURE: "전시 · 문화", ETC: "기타" };

export function CreatorReviewScreen() {
  const [status, setStatus] = useState<"PENDING" | "REVIEWED">("PENDING");
  const { data, isPending, isError, error, refetch } = useCreatorApplications(status);
  const review = useReviewCreator();
  const toast = useToast();
  const [rejecting, setRejecting] = useState<CreatorApplicationReview | null>(null);

  return (
    <div className="pb-10">
      <TopBar title="크리에이터 심사" backHref={routes.admin()} />
      <div className="border-b border-line px-5">
        <Tabs
          items={[
            { value: "PENDING", label: "심사 대기" },
            { value: "REVIEWED", label: "심사 완료" },
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
        <EmptyState title={status === "PENDING" ? "심사할 신청이 없어요" : "심사한 신청이 없어요"} />
      ) : (
        <ul className="space-y-3 px-5 pt-4">
          {data.map((a) => (
            <li key={a.id} className="rounded-card border border-line p-4">
              <div className="flex items-center gap-3">
                <Avatar src={a.applicant?.profileImageUrl} name={a.applicant?.nickname ?? "?"} size={40} />
                <div className="min-w-0 flex-1">
                  <p className="text-[14px] font-bold">
                    {a.applicant?.nickname} <span className="font-normal text-ink-sub">@{a.applicant?.username}</span>
                  </p>
                  <p className="text-[12px] text-ink-sub">
                    {CATEGORY[a.category] ?? a.category} · {formatDotDate(a.submittedAt)} 신청
                  </p>
                </div>
                {a.status !== "PENDING" && <Badge tone={a.status === "APPROVED" ? "lime" : "danger"}>{a.status === "APPROVED" ? "승인" : "반려"}</Badge>}
              </div>
              <dl className="mt-3 space-y-2 text-[13px]">
                <div>
                  <dt className="text-[12px] font-bold text-ink-faint">소개</dt>
                  <dd className="mt-0.5 leading-relaxed">{a.intro}</dd>
                </div>
                <div>
                  <dt className="text-[12px] font-bold text-ink-faint">만들고 싶은 컬렉션</dt>
                  <dd className="mt-0.5">{a.plan}</dd>
                </div>
                {a.channelUrl && (
                  <div>
                    <dt className="text-[12px] font-bold text-ink-faint">채널</dt>
                    <dd className="mt-0.5">
                      <a href={a.channelUrl} target="_blank" rel="noreferrer noopener" className="inline-flex items-center gap-1 text-primary-deep underline underline-offset-2">
                        {a.channelUrl} <ExternalLink size={12} />
                      </a>
                    </dd>
                  </div>
                )}
              </dl>
              {a.status === "PENDING" ? (
                <div className="mt-4 grid grid-cols-2 gap-2">
                  <Button size="md" variant="outline" onClick={() => setRejecting(a)}>
                    반려
                  </Button>
                  <Button
                    size="md"
                    loading={review.isPending && review.variables?.id === a.id}
                    onClick={() =>
                      review.mutate(
                        { id: a.id, approve: true, rejectReason: "" },
                        { onSuccess: () => toast({ message: `${a.applicant?.nickname}님을 크리에이터로 승인했어요` }) },
                      )
                    }
                  >
                    승인
                  </Button>
                </div>
              ) : (
                a.rejectReason && <p className="mt-3 text-[12px] text-danger">반려 사유: {a.rejectReason}</p>
              )}
            </li>
          ))}
        </ul>
      )}
      {review.isError && !rejecting && <Notice tone="danger" className="mx-5 mt-3">{errorMessage(review.error)}</Notice>}

      <ReasonSheet
        open={!!rejecting}
        title="신청 반려"
        description={`${rejecting?.applicant?.nickname ?? ""}님에게 반려 사유가 안내되고, 내용을 보완해 다시 신청할 수 있어요.`}
        placeholder="반려 사유 (예: 직접 촬영한 사진 · 후기가 더 필요해요)"
        confirmLabel="반려"
        danger
        loading={review.isPending}
        error={review.isError ? review.error : null}
        onClose={() => {
          setRejecting(null);
          review.reset();
        }}
        onConfirm={(reason) =>
          rejecting &&
          review.mutate(
            { id: rejecting.id, approve: false, rejectReason: reason },
            {
              onSuccess: () => {
                toast({ message: "신청을 반려했어요" });
                setRejecting(null);
              },
            },
          )
        }
      />
    </div>
  );
}
