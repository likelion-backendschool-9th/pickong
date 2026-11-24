"use client";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { formatDotDate } from "@/shared/lib/date";
import { Switch } from "@/shared/ui/controls";
import { Badge, ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useAgreements, useUpdateAgreement, type TermsType } from "../_api/accountApi";

const TERMS_PATH: Record<TermsType, "service" | "privacy" | "marketing"> = { SERVICE: "service", PRIVACY: "privacy", MARKETING: "marketing" };

export function AgreementsScreen() {
  const { data, isPending, isError, error, refetch } = useAgreements();
  const update = useUpdateAgreement();
  const toast = useToast();

  return (
    <div>
      <TopBar title="약관 및 동의 관리" backHref={routes.settings()} />
      {isPending ? (
        <Skeleton className="m-5 h-48" />
      ) : isError ? (
        <ErrorState message={errorMessage(error)} onRetry={() => refetch()} />
      ) : (
        <div className="px-5 pt-2 pb-8">
          <ul className="divide-y divide-line">
            {data.map((a) => (
              <li key={a.type} className="py-4">
                <div className="flex items-center gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[15px] font-bold">{a.title}</span>
                      <Badge tone={a.required ? "lime" : "neutral"}>{a.required ? "필수" : "선택"}</Badge>
                    </div>
                    <p className="mt-1 text-[12px] text-ink-sub">
                      {a.agreed && a.agreedAt ? `${formatDotDate(a.agreedAt)} 동의 · 버전 ${a.version}` : "동의하지 않음"}
                    </p>
                  </div>
                  {a.required ? (
                    <span className="text-[12px] font-bold text-primary-deep">동의함</span>
                  ) : (
                    <Switch
                      label={`${a.title} 동의`}
                      hideLabel
                      checked={a.agreed}
                      disabled={update.isPending} // 요청 중에는 막아 연달아 눌러 같은 요청이 겹치지 않게 (중복 기록은 서버가 막음)
                      onChange={(agreed) =>
                        update.mutate(
                          { type: a.type, agreed },
                          { onSuccess: () => toast({ message: agreed ? "마케팅 정보 수신에 동의했어요" : "마케팅 정보 수신 동의를 철회했어요" }) },
                        )
                      }
                    />
                  )}
                </div>
                <Link href={routes.terms(TERMS_PATH[a.type])} className="mt-2 inline-flex items-center gap-0.5 text-[13px] text-ink-sub">
                  전문 보기 <ChevronRight size={14} />
                </Link>
              </li>
            ))}
          </ul>
          {update.isError && <Notice tone="danger" className="mt-3">{errorMessage(update.error)}</Notice>}
          <p className="mt-4 text-[12px] leading-relaxed text-ink-faint">
            필수 약관은 서비스 이용에 꼭 필요해서 철회할 수 없어요. 더 이상 이용하지 않으려면{" "}
            <Link href={routes.withdraw()} className="underline underline-offset-2">
              회원 탈퇴
            </Link>
            를 진행해 주세요.
          </p>
        </div>
      )}
    </div>
  );
}
