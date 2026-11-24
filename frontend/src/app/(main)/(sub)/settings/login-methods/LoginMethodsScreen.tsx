"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { SocialLogo } from "@/shared/auth/SocialLogo";
import { parseSocialProvider, SOCIAL_PROVIDER_LABEL, type SocialProvider } from "@/shared/auth/socialProviders";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
import { formatDotDate } from "@/shared/lib/date";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { Button } from "@/shared/ui/Button";
import { Badge, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useSocialAccounts, useStartSocialLink, useUnlinkSocialAccount } from "../_api/accountApi";
import { isSameOriginPath, linkErrorMessage, loginMethodRows } from "./_lib/loginMethods";

const LOGO_BG: Record<SocialProvider, string> = {
  KAKAO: "bg-kakao",
  GOOGLE: "border border-line bg-bg",
};

/**
 * 로그인 수단 관리: 카카오 · Google 계정 연결 · 해제.
 * 연결한 계정 중 어느 것으로 로그인해도 같은 찜콩 계정으로 들어오며, 가입할 때 사용한 수단은 해제할 수 없습니다.
 */
export function LoginMethodsScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const toast = useToast();
  const { data, isPending, isError, error, refetch } = useSocialAccounts();
  const startLink = useStartSocialLink();
  const unlink = useUnlinkSocialAccount();
  const [target, setTarget] = useState<SocialProvider | null>(null);

  // 연결 콜백 결과(?linked= · ?error=)는 한 번만 알리고 주소에서 지워, 새로고침해도 다시 뜨지 않게 합니다.
  const [linkError] = useState(() => linkErrorMessage(params.get("error")));
  const handled = useRef(false);
  useEffect(() => {
    if (handled.current) return;
    handled.current = true;
    const linked = parseSocialProvider(params.get("linked"));
    if (linked) toast({ message: `${SOCIAL_PROVIDER_LABEL[linked]} 계정을 연결했어요` });
    if (params.size > 0) router.replace(pathname, { scroll: false });
  }, [params, pathname, router, toast]);

  const connect = (provider: SocialProvider) =>
    startLink.mutate(provider, {
      onSuccess: ({ authorizationUrl }) => {
        if (isSameOriginPath(authorizationUrl)) window.location.assign(authorizationUrl);
      },
    });

  const rows = data ? loginMethodRows(data) : [];
  const hasSignupMethod = rows.some((r) => r.isSignupMethod);

  return (
    <div>
      <TopBar title="로그인 수단 관리" backHref={routes.settings()} />
      <p className="px-5 pt-1 pb-3 text-[13px] leading-relaxed text-ink-sub">
        연결한 계정 중 어느 것으로 로그인해도 같은 찜콩 계정으로 들어와요.
      </p>

      {linkError && (
        <Notice tone="danger" className="mx-5 mb-2">
          {linkError}
        </Notice>
      )}

      {isPending ? (
        <Skeleton className="mx-5 h-36" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <ul className="divide-y divide-line px-5">
          {rows.map(({ provider, account, canUnlink, isSignupMethod }) => {
            const label = SOCIAL_PROVIDER_LABEL[provider];
            const connecting = startLink.isPending && startLink.variables === provider;
            return (
              <li key={provider} className="flex min-h-[72px] items-center gap-3 py-3">
                <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-full", LOGO_BG[provider])}>
                  <SocialLogo provider={provider} size={18} />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-1.5">
                    <span className="text-[15px] font-bold">{label}</span>
                    {account && <Badge>연결됨</Badge>}
                    {isSignupMethod && <Badge tone="neutral">가입한 수단</Badge>}
                  </div>
                  <p className="mt-0.5 truncate text-[12px] text-ink-sub">
                    {account ? `${formatDotDate(account.connectedAt)} 연결 · ${account.email ?? "이메일 비공개"}` : "연결되지 않음"}
                  </p>
                </div>
                {account ? (
                  // 가입한 수단은 해제할 수 없어서 버튼을 보여 주지 않습니다(목록 아래 안내).
                  canUnlink && (
                    <Button variant="outline" size="sm" onClick={() => setTarget(provider)} aria-label={`${label} 연결 해제`}>
                      연결 해제
                    </Button>
                  )
                ) : (
                  <Button variant="secondary" size="sm" loading={connecting} disabled={startLink.isPending} onClick={() => connect(provider)}>
                    연결하기
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {hasSignupMethod && <Notice className="mx-5 mt-3">가입할 때 사용한 로그인 수단은 해제할 수 없어요.</Notice>}
      {startLink.isError && (
        <Notice tone="danger" className="mx-5 mt-3">
          {errorMessage(startLink.error)}
        </Notice>
      )}

      <BottomSheet
        open={target !== null}
        onClose={() => {
          setTarget(null);
          unlink.reset();
        }}
        title={target ? `${SOCIAL_PROVIDER_LABEL[target]} 연결을 해제할까요?` : ""}
      >
        {target && (
          <div className="pb-[calc(12px+env(safe-area-inset-bottom))]">
            <p className="text-[14px] text-ink-sub">
              해제하면 {SOCIAL_PROVIDER_LABEL[target]} 계정으로는 이 찜콩 계정에 로그인할 수 없어요. 나중에 다시 연결할 수 있어요.
            </p>
            {unlink.isError && (
              <Notice tone="danger" className="mt-3">
                {errorMessage(unlink.error)}
              </Notice>
            )}
            <div className="mt-5 grid grid-cols-2 gap-2">
              <Button variant="outline" block onClick={() => setTarget(null)}>
                취소
              </Button>
              <Button
                variant="danger"
                block
                loading={unlink.isPending}
                onClick={() =>
                  unlink.mutate(target, {
                    onSuccess: () => {
                      toast({ message: `${SOCIAL_PROVIDER_LABEL[target]} 연결을 해제했어요` });
                      setTarget(null);
                    },
                  })
                }
              >
                연결 해제
              </Button>
            </div>
          </div>
        )}
      </BottomSheet>
    </div>
  );
}
