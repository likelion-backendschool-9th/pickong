"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { authApi } from "@/shared/auth/authApi";
import { routes } from "@/shared/config/routes";
import { ErrorState, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { AuthCheckbox, AuthHeading, pillButtonClass } from "../../_components/AuthUi";
import { useAgreePendingTerms, usePendingTerms, type TermsType } from "./_api/consentApi";
import { TERMS_PATH, canSubmit, hasRequired, safeNext, toggleAll } from "./_lib/consent";

/**
 * 바뀐 약관 재동의. 로그인 직후(서버가 보냄)나, 쓰는 도중 403 TERMS_RECONSENT_REQUIRED를 받았을 때(QueryClient가 보냄) 옵니다.
 * 필수 약관은 동의해야 계속 쓸 수 있고, 동의하지 않으려면 로그아웃합니다(탈퇴는 탈퇴 기능을 만들 때 추가).
 * 선택 약관만 남았으면 체크하지 않고 '확인'을 눌러도 되고, 그러면 서버가 예전 동의를 철회해 다시 묻지 않습니다.
 *
 * @param next 동의한 뒤 돌아갈 곳(?next=). 찜콩 안 경로만 따르고, 없으면 피드
 */
export function ConsentScreen({ next }: { next?: string }) {
  const router = useRouter();
  const qc = useQueryClient();
  const destination = safeNext(next);
  const { data: pending, isPending, isError, error, refetch } = usePendingTerms();
  const agree = useAgreePendingTerms();
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      router.replace(routes.start());
      qc.clear();
    },
  });
  const [checked, setChecked] = useState<Set<TermsType>>(() => new Set());

  // 동의할 것이 없으면(이미 동의했거나 주소로 직접 들어옴) 바로 돌려보냅니다.
  useEffect(() => {
    if (pending?.length === 0) router.replace(destination);
  }, [pending, destination, router]);

  /** 막혀서 실패해 있던 요청이 남지 않게 캐시를 비우고 돌아갑니다. */
  const submit = () =>
    agree.mutate([...checked], {
      onSuccess: () => {
        qc.clear();
        router.replace(destination);
      },
    });

  const toggle = (type: TermsType, value: boolean) =>
    setChecked((prev) => {
      const nextChecked = new Set(prev);
      if (value) nextChecked.add(type);
      else nextChecked.delete(type);
      return nextChecked;
    });

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="" hideBack />
      {isPending || pending?.length === 0 ? (
        <div className="space-y-3 px-5 pt-4">
          <Skeleton className="h-9 w-2/3" />
          <Skeleton className="h-40" />
        </div>
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <div className="flex flex-1 flex-col px-5 pt-1 pb-[calc(24px+env(safe-area-inset-bottom))]">
          <AuthHeading
            title="바뀐 약관을 확인해 주세요"
            description={
              hasRequired(pending)
                ? "필수 약관에 동의하면 찜콩을 계속 이용할 수 있어요."
                : "선택 약관이라 동의하지 않아도 계속 이용할 수 있어요."
            }
          />

          <fieldset className="mt-7">
            <legend className="sr-only">바뀐 약관 동의</legend>
            {pending.length > 1 && (
              <div className="mb-3 rounded-[14px] bg-soft px-5 py-4">
                <AuthCheckbox
                  className="text-[16px] font-bold text-ink"
                  checked={pending.every((terms) => checked.has(terms.type))}
                  onChange={() => setChecked(toggleAll(pending, checked))}
                >
                  모두 동의해요
                </AuthCheckbox>
              </div>
            )}
            <ul className="space-y-1 px-1">
              {pending.map((terms) => (
                <li key={terms.type} className="flex items-center justify-between gap-3 py-1">
                  <AuthCheckbox
                    className="text-[15px] text-ink-sub"
                    checked={checked.has(terms.type)}
                    onChange={(value) => toggle(terms.type, value)}
                  >
                    [{terms.required ? "필수" : "선택"}] {terms.title}
                    <span className="ml-1 text-[13px] text-ink-faint">버전 {terms.version}</span>
                  </AuthCheckbox>
                  {/* 새 탭: 약관을 읽고 돌아와도 체크한 상태가 남게 */}
                  <Link
                    href={routes.terms(TERMS_PATH[terms.type])}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="shrink-0 px-1 py-1 text-[15px] text-ink-sub underline underline-offset-2"
                    aria-label={`${terms.title} 보기 (새 탭)`}
                  >
                    보기
                  </Link>
                </li>
              ))}
            </ul>
          </fieldset>

          <div className="mt-auto pt-8">
            {agree.isError && (
              <p className="mb-3 text-[13px] text-danger" role="alert">
                {errorMessage(agree.error)}
              </p>
            )}
            <button
              type="button"
              disabled={!canSubmit(pending, checked) || agree.isPending}
              aria-busy={agree.isPending || undefined}
              onClick={submit}
              className={pillButtonClass}
            >
              {agree.isPending ? "저장하는 중…" : hasRequired(pending) || checked.size > 0 ? "동의하고 계속하기" : "확인"}
            </button>
            {hasRequired(pending) && (
              <p className="mt-4 text-center text-[13px] text-ink-sub">
                동의하지 않으면 찜콩을 이용할 수 없어요.{" "}
                <button
                  type="button"
                  onClick={() => logout.mutate()}
                  disabled={logout.isPending}
                  className="font-bold underline underline-offset-2"
                >
                  로그아웃
                </button>
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
