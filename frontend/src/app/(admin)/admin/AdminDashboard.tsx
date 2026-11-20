"use client";

import { useMutation } from "@tanstack/react-query";
import { BadgeCheck, ChevronRight, EyeOff, Flag, Landmark, LogOut, ReceiptText, UserX } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { authApi } from "@/shared/auth/authApi";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { Skeleton } from "@/shared/ui/display";
import { PageHeader } from "@/shared/ui/layout";
import { useAdminSummary } from "./_api/adminApi";

/** 관리자 홈: 처리할 일 개수와 각 관리 화면으로 가는 메뉴 */
export function AdminDashboard() {
  const router = useRouter();
  const { data } = useAdminSummary();
  const logout = useMutation({ mutationFn: authApi.logout, onSuccess: () => router.replace(routes.start()) });

  const todo = data ? data.pendingReports + data.pendingCreatorApplications + data.pendingRefunds + data.scheduledSettlements : null;

  return (
    <div className="pb-10">
      <PageHeader
        label="Admin"
        title="찜콩 관리자"
        right={
          <button
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="inline-flex h-9 items-center gap-1 rounded-control border border-line px-3 text-[13px] font-medium"
          >
            <LogOut size={14} /> 로그아웃
          </button>
        }
      />
      <div className="px-5 pt-4">
        <div className="rounded-card bg-soft px-5 py-4">
          <p className="text-[13px] text-ink-sub">지금 처리할 일</p>
          {todo === null ? <Skeleton className="mt-1 h-8 w-20" /> : <p className="mt-0.5 text-[28px] font-bold tabular-nums">{todo}건</p>}
        </div>

        <h2 className="mt-7 mb-2 text-[13px] font-bold text-ink-faint">운영</h2>
        <ul className="space-y-2">
          <Menu href={routes.adminReports()} icon={<Flag size={18} />} label="신고 처리" desc="게시물 · 댓글 · 사용자 · 컬렉션 신고" count={data?.pendingReports} />
          <Menu href={routes.adminUsers()} icon={<UserX size={18} />} label="사용자 제재" desc="경고 · 기간 정지 · 영구 정지" count={data?.sanctionedUsers} countLabel="제재 중" />
          <Menu href={routes.adminContents()} icon={<EyeOff size={18} />} label="콘텐츠 관리" desc="게시물 · 컬렉션 숨김" count={data?.hiddenContents} countLabel="숨김" />
        </ul>

        <h2 className="mt-7 mb-2 text-[13px] font-bold text-ink-faint">크리에이터 · 결제</h2>
        <ul className="space-y-2">
          <Menu href={routes.adminCreators()} icon={<BadgeCheck size={18} />} label="크리에이터 심사" desc="신청 승인 · 반려" count={data?.pendingCreatorApplications} />
          <Menu href={routes.adminRefunds()} icon={<ReceiptText size={18} />} label="환불 처리" desc="유료 컬렉션 환불 신청" count={data?.pendingRefunds} />
          <Menu href={routes.adminSettlements()} icon={<Landmark size={18} />} label="정산 처리" desc="월별 크리에이터 정산 지급" count={data?.scheduledSettlements} countLabel="지급 예정" />
        </ul>
      </div>
    </div>
  );
}

function Menu({ href, icon, label, desc, count, countLabel = "대기" }: { href: string; icon: ReactNode; label: string; desc: string; count?: number; countLabel?: string }) {
  return (
    <li>
      <Link href={href} className="flex items-center gap-3 rounded-card border border-line px-4 py-3.5 active:bg-fill">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-soft text-primary-deep">{icon}</span>
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] font-bold">{label}</span>
          <span className="block truncate text-[12px] text-ink-sub">{desc}</span>
        </span>
        {count !== undefined && count > 0 && (
          <span className={cn("rounded-full px-2 py-0.5 text-[12px] font-bold tabular-nums", countLabel === "대기" ? "bg-danger-soft text-danger" : "bg-fill text-ink-sub")}>
            {countLabel} {count}
          </span>
        )}
        <ChevronRight size={18} className="text-ink-faint" />
      </Link>
    </li>
  );
}
