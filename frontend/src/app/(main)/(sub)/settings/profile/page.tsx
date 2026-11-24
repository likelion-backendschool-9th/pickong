"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  NAME_MAX,
  NICKNAME_MAX,
  NicknameStatusIcon,
  nameError,
  normalizeName,
  nicknameFeedback,
  normalizeNicknameInput,
  useMe,
  useNicknameCheck,
  useUpdateMe,
  type User,
} from "@/entities/user";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { cn } from "@/shared/lib/cn";
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
  // 닉네임 = 사용자 ID: 바꾸면 프로필 주소(/users/닉네임)도 함께 바뀝니다.
  const [nickname, setNickname] = useState(me.username);
  // 이름: 중복 가능, 비우면 null(화면에는 닉네임이 대신 보임)
  const [name, setName] = useState(me.name ?? "");
  const [bio, setBio] = useState(me.bio);
  const [region, setRegion] = useState(me.region);

  const status = useNicknameCheck(nickname, me.username);
  const { error: nicknameErr, hint } = nicknameFeedback(nickname, status);
  const nicknameOk = status === "available" || status === "current";
  const nameErr = nameError(name);

  return (
    <Screen>
      <TopBar title="프로필 편집" backHref={routes.user(me.username)} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        <div className="flex flex-col items-center gap-2">
          <Avatar src={me.profileImageUrl} name={name.trim() || nickname || me.username} size={84} />
          <span className="text-[12px] text-ink-faint">사진 변경은 업로드 API 연결 후 제공돼요</span>
        </div>
        <Field
          label="닉네임"
          htmlFor="pf-nick"
          error={nicknameErr}
          hint={
            <span aria-live="polite" className={cn(status === "available" && "text-primary-deep")}>
              {status === "available" ? `${hint} 저장하면 프로필 주소도 바뀌어요.` : hint}
            </span>
          }
        >
          <div className="relative">
            <span className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-[15px] text-ink-faint" aria-hidden>
              @
            </span>
            <Input
              id="pf-nick"
              value={nickname}
              onChange={(e) => setNickname(normalizeNicknameInput(e.target.value))}
              maxLength={NICKNAME_MAX}
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              inputMode="email"
              lang="en"
              aria-invalid={!!nicknameErr || undefined}
              className={cn("pr-12 pl-9", nicknameErr && "border-danger")}
            />
            <NicknameStatusIcon status={status} />
          </div>
        </Field>
        <Field
          label="이름"
          htmlFor="pf-name"
          optional
          error={nameErr}
          hint="다른 사람과 같아도 돼요. 비워 두면 닉네임이 대신 보여요."
        >
          <Input
            id="pf-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="이름을 입력해 주세요"
            maxLength={NAME_MAX}
            autoComplete="name"
            aria-invalid={!!nameErr || undefined}
          />
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
          disabled={!nicknameOk || !!nameErr}
          loading={update.isPending}
          onClick={() =>
            update.mutate(
              { username: nickname, name: normalizeName(name), bio, region },
              {
                onSuccess: (u) => {
                  toast({ message: "프로필을 저장했어요" });
                  router.replace(routes.user(u.username));
                },
              },
            )
          }
        >
          프로필 저장
        </Button>
      </BottomCta>
    </Screen>
  );
}
