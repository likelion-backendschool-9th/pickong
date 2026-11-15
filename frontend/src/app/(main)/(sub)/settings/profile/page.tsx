"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useMe, useUpdateMe, type User } from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Avatar, Notice, Skeleton } from "@/shared/ui/display";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/Toast";

export default function EditProfilePage() {
  const { data: me } = useMe();
  if (!me) return <Skeleton className="h-dvh rounded-none" />;
  return <EditProfileForm me={me} />;
}

function EditProfileForm({ me }: { me: User }) {
  const router = useRouter();
  const toast = useToast();
  const update = useUpdateMe();
  const [nickname, setNickname] = useState(me.nickname);
  const [bio, setBio] = useState(me.bio);
  const [region, setRegion] = useState(me.region);

  return (
    <Screen>
      <TopBar title="프로필 편집" backHref={routes.user(me.username)} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        <div className="flex flex-col items-center gap-2">
          <Avatar src={me.profileImageUrl} name={me.nickname} size={84} />
          <span className="text-[12px] text-ink-faint">사진 변경은 업로드 API 연결 후 제공돼요</span>
        </div>
        <Field label="닉네임" htmlFor="pf-nick" error={!nickname.trim() ? "닉네임을 입력해 주세요" : undefined}>
          <Input id="pf-nick" value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} />
        </Field>
        <Field label="소개" htmlFor="pf-bio">
          <Textarea id="pf-bio" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={150} className="min-h-24" />
        </Field>
        <Field label="활동 지역" htmlFor="pf-region" optional>
          <Input id="pf-region" value={region} onChange={(e) => setRegion(e.target.value)} placeholder="서울" maxLength={20} />
        </Field>
        {update.isError && <Notice tone="danger">{errorMessage(update.error)}</Notice>}
      </div>
      <BottomCta>
        <Button
          block
          disabled={!nickname.trim()}
          loading={update.isPending}
          onClick={() =>
            update.mutate(
              { nickname: nickname.trim(), bio, region },
              { onSuccess: (u) => { toast({ message: "프로필을 저장했어요" }); router.replace(routes.user(u.username)); } },
            )
          }
        >
          프로필 저장
        </Button>
      </BottomCta>
    </Screen>
  );
}
