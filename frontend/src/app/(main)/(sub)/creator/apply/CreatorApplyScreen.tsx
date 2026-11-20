"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { CheckMark, Chip } from "@/shared/ui/controls";
import { ErrorState, Notice, Skeleton } from "@/shared/ui/display";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";
import { useApplyCreator, useCreator, type CreatorCategory } from "../_api/creatorApi";
import { CREATOR_CATEGORIES, INTRO_MIN, channelUrlError, introError } from "../_lib/creator";

export function CreatorApplyScreen() {
  const { data, isPending, isError, error } = useCreator();

  if (isPending) return <Skeleton className="h-dvh rounded-none" />;
  if (isError) return <ErrorState message={errorMessage(error)} />;
  if (data.status === "APPROVED" || data.status === "PENDING") {
    return (
      <Screen>
        <TopBar title="크리에이터 신청" backHref={routes.creator()} />
        <ErrorState message={data.status === "APPROVED" ? "이미 크리에이터로 활동 중이에요." : "심사 중인 신청이 있어요. 결과가 나오면 알려 드릴게요."} />
      </Screen>
    );
  }
  return <ApplyForm previous={data.application} />;
}

function ApplyForm({ previous }: { previous: { category: CreatorCategory; channelUrl: string; intro: string; plan: string } | null }) {
  const router = useRouter();
  const toast = useToast();
  const apply = useApplyCreator();
  // 반려 후 다시 신청할 때는 이전 내용을 채워 둡니다.
  const [category, setCategory] = useState<CreatorCategory | null>(previous?.category ?? null);
  const [channelUrl, setChannelUrl] = useState(previous?.channelUrl ?? "");
  const [intro, setIntro] = useState(previous?.intro ?? "");
  const [plan, setPlan] = useState(previous?.plan ?? "");
  const [agreed, setAgreed] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const errors = {
    category: !category ? "활동 분야를 골라 주세요." : null,
    channelUrl: channelUrlError(channelUrl),
    intro: introError(intro),
    plan: !plan.trim() ? "만들고 싶은 컬렉션을 적어 주세요." : null,
  };
  const show = (k: keyof typeof errors) => (submitted ? errors[k] : null);

  const submit = () => {
    setSubmitted(true);
    if (Object.values(errors).some(Boolean) || !agreed || !category) return;
    apply.mutate(
      { category, channelUrl: channelUrl.trim(), intro: intro.trim(), plan: plan.trim() },
      {
        onSuccess: () => {
          toast({ message: "크리에이터 신청을 보냈어요", detail: "심사 결과는 알림으로 알려 드려요" });
          router.replace(routes.creator());
        },
      },
    );
  };

  return (
    <Screen>
      <TopBar title="크리에이터 신청" backHref={routes.creator()} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        <div>
          <h2 className="text-[22px] leading-snug font-bold">어떤 장소를 소개하고 싶나요?</h2>
          <p className="mt-2 text-[13px] text-ink-sub">운영팀이 내용을 확인한 뒤 결과를 알려 드려요.</p>
        </div>

        <div>
          <p className="mb-2 text-[13px] font-bold">활동 분야</p>
          <div className="flex flex-wrap gap-2">
            {CREATOR_CATEGORIES.map((c) => (
              <Chip key={c.value} selected={category === c.value} onClick={() => setCategory(c.value)}>
                {c.label}
              </Chip>
            ))}
          </div>
          {show("category") && <p className="mt-2 text-[12px] text-danger">{show("category")}</p>}
        </div>

        <Field label="소개" htmlFor="creator-intro" error={show("intro")} hint={`${intro.trim().length}자 · ${INTRO_MIN}자 이상`}>
          <Textarea
            id="creator-intro"
            value={intro}
            maxLength={300}
            onChange={(e) => setIntro(e.target.value)}
            placeholder="어떤 장소를 얼마나 오래 다녔는지, 나만의 기준이 있다면 알려 주세요."
          />
        </Field>

        <Field label="만들고 싶은 유료 컬렉션" htmlFor="creator-plan" error={show("plan")}>
          <Input id="creator-plan" value={plan} maxLength={60} onChange={(e) => setPlan(e.target.value)} placeholder="예) 성수 숨은 작업 카페 15곳" />
        </Field>

        <Field label="활동 채널" htmlFor="creator-channel" optional error={show("channelUrl")} hint="블로그 · 인스타그램 등 참고할 수 있는 주소">
          <Input id="creator-channel" type="url" inputMode="url" value={channelUrl} onChange={(e) => setChannelUrl(e.target.value)} placeholder="https://" />
        </Field>

        <button type="button" role="checkbox" aria-checked={agreed} onClick={() => setAgreed((v) => !v)} className="flex w-full items-start gap-2.5 text-left">
          <CheckMark checked={agreed} />
          <span className="text-[12px] leading-relaxed text-ink-sub">
            판매 금액의 20%가 수수료로 빠지고, 다른 사람의 사진 · 글을 허락 없이 쓰면 판매가 중단될 수 있다는 크리에이터 운영 정책에 동의해요.
          </span>
        </button>
        {submitted && !agreed && <p className="-mt-4 text-[12px] text-danger">운영 정책에 동의해 주세요.</p>}

        {apply.isError && <Notice tone="danger">{errorMessage(apply.error)}</Notice>}
      </div>
      <BottomCta>
        <Button block loading={apply.isPending} onClick={submit}>
          신청하기
        </Button>
      </BottomCta>
    </Screen>
  );
}
