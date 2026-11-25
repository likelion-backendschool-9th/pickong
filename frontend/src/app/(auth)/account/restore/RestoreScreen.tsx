"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { AuthHeading, pillButtonClass } from "../../_components/AuthUi";
import { useCancelWithdrawal, useLeaveRestore, useRestoreSession } from "./_api/restoreApi";

const RESTORE_SESSION_EXPIRED = "RESTORE_SESSION_EXPIRED";
const WITHDRAWAL_NOT_CANCELLABLE = "WITHDRAWAL_NOT_CANCELLABLE";

const codeOf = (error: unknown) => (error instanceof ApiError ? error.code : null);

/**
 * 탈퇴 취소. 탈퇴 유예 중(30일 안)인 회원이 소셜 로그인하면 서버가 로그인 세션 대신 탈퇴 취소 쿠키(10분)를 주고 이 화면으로 보냅니다.
 * - 취소: 서버가 ACTIVE로 돌리고 세션을 발급 → 피드로 (바뀐 약관이 있으면 피드에서 재동의 화면으로 이동)
 * - 탈퇴 유지: 탈퇴 취소 쿠키만 지우고 시작 화면으로
 * 쿠키가 만료되면 로그인 화면으로 보내 안내하고(다시 로그인하면 새로 받음), 30일이 지났으면 취소할 수 없다고 알립니다.
 */
export function RestoreScreen() {
  const router = useRouter();
  const toast = useToast();
  const session = useRestoreSession();
  const cancel = useCancelWithdrawal();
  const leave = useLeaveRestore();
  const failure = codeOf(session.error) ?? codeOf(cancel.error);

  // 탈퇴 취소 쿠키가 없거나 만료: 로그인 화면에서 사유를 보여 줍니다.
  // 취소에 성공한 뒤에는 서버가 쿠키를 지웠으므로 같은 오류가 와도 보내지 않습니다(피드로 가는 중).
  useEffect(() => {
    if (failure === RESTORE_SESSION_EXPIRED && !cancel.isSuccess) router.replace(routes.login(RESTORE_SESSION_EXPIRED));
  }, [failure, cancel.isSuccess, router]);

  /**
   * 취소하면 서버가 세션 쿠키를 주므로 바로 피드로 갑니다.
   * 캐시는 비우지 않습니다. 이 화면은 로그인 콜백에서 새로 열린 페이지라 다른 계정의 캐시가 없고,
   * 비우면 아직 떠 있는 이 화면이 탈퇴 상태를 다시 불러와(쿠키가 지워져 401) 로그인 화면으로 잘못 보내기 때문입니다.
   */
  const restore = () =>
    cancel.mutate(undefined, {
      onSuccess: () => {
        toast({ message: "탈퇴를 취소했어요. 다시 찜콩을 이용할 수 있어요." });
        router.replace(routes.feed());
      },
    });

  /**
   * 탈퇴 유지: 탈퇴 취소 쿠키를 지우고 시작 화면으로. 실패해도(네트워크 등) 탈퇴는 이미 유지 상태이고 쿠키는 10분 뒤 만료되므로
   * 그대로 시작 화면으로 보내되, 왜 이동했는지 알 수 있게 알림을 띄웁니다.
   */
  const keepWithdrawal = () =>
    leave.mutate(undefined, {
      onError: () => toast({ message: "탈퇴는 그대로 유지돼요." }),
      onSettled: () => router.replace(routes.start()),
    });

  if (session.isPending || failure === RESTORE_SESSION_EXPIRED) {
    return (
      <div className="min-h-dvh">
        <TopBar title="" hideBack />
        <div className="space-y-3 px-5 pt-4">
          <Skeleton className="h-9 w-2/3" />
          <Skeleton className="h-32" />
        </div>
      </div>
    );
  }

  if (failure === WITHDRAWAL_NOT_CANCELLABLE) {
    return (
      <div className="flex min-h-dvh flex-col">
        <TopBar title="" hideBack />
        <div className="flex flex-1 flex-col px-5 pt-1 pb-[calc(24px+env(safe-area-inset-bottom))]">
          <AuthHeading
            title="탈퇴를 취소할 수 없어요"
            description="탈퇴를 신청한 지 30일이 지나 계정 정보가 지워졌거나 지워지는 중이에요. 같은 소셜 계정으로 새로 가입할 수 있어요."
          />
          <button type="button" className={`mt-auto ${pillButtonClass}`} onClick={keepWithdrawal}>
            처음으로
          </button>
        </div>
      </div>
    );
  }

  if (session.isError) {
    return (
      <div className="min-h-dvh">
        <TopBar title="" hideBack />
        <ErrorState message={errorMessage(session.error)} onRetry={() => session.refetch()} />
      </div>
    );
  }

  const { username, requestedAt, anonymizeAt } = session.data;
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="" hideBack />
      <div className="flex flex-1 flex-col px-5 pt-1 pb-[calc(24px+env(safe-area-inset-bottom))]">
        <AuthHeading
          title="탈퇴를 신청한 계정이에요"
          description={`@${username} 계정은 ${formatDotDate(requestedAt)}에 탈퇴를 신청했어요.`}
        />
        <ul className="mt-7 space-y-2 rounded-card bg-soft px-4 py-4 text-[14px] leading-relaxed text-ink">
          <li>
            <b>{formatDotDate(anonymizeAt)}</b>까지 탈퇴를 취소하고 그대로 이용할 수 있어요.
          </li>
          <li>그 뒤에는 이름 · 이메일 · 사진 같은 정보와 게시물이 지워지고 되돌릴 수 없어요.</li>
        </ul>

        <div className="mt-auto pt-8">
          {cancel.isError && (
            <p className="mb-3 text-[13px] text-danger" role="alert">
              {errorMessage(cancel.error)}
            </p>
          )}
          <button
            type="button"
            className={pillButtonClass}
            disabled={cancel.isPending || leave.isPending}
            aria-busy={cancel.isPending || undefined}
            onClick={restore}
          >
            {cancel.isPending ? "되돌리는 중…" : "탈퇴 취소하고 계속 이용하기"}
          </button>
          <button
            type="button"
            className="mt-3 w-full py-2 text-[14px] text-ink-sub underline underline-offset-2"
            disabled={cancel.isPending || leave.isPending}
            onClick={keepWithdrawal}
          >
            탈퇴 유지하기
          </button>
        </div>
      </div>
    </div>
  );
}
