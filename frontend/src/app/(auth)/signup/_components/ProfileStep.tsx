"use client";

import type { FormEvent } from "react";
import { NAME_MAX, nameError } from "@/entities/user";
import { SOCIAL_PROVIDER_LABEL, type SocialProvider } from "@/shared/auth/socialProviders";
import { Avatar } from "@/shared/ui/display";
import { AuthField, AuthInput, pillButtonClass } from "../../_components/AuthUi";
import { SignupScreen, type StepFrame } from "./SignupUi";

/**
 * 2단계: 프로필 사진 · 이름. 둘 다 선택이라 건너뛸 수 있습니다.
 * 이름은 다른 사람과 같아도 되고, 비워 두면 화면에는 닉네임이 대신 보입니다. 가입 후 프로필 편집에서 바꿀 수 있습니다.
 */
export function ProfileStep({
  frame,
  provider,
  nickname,
  socialImageUrl,
  useSocialImage,
  name,
  onChange,
  onNext,
  onSkip,
}: {
  frame: StepFrame;
  provider: SocialProvider;
  /** 1단계에서 정한 닉네임: 이름을 비우면 대신 보입니다. */
  nickname: string;
  /** 소셜 계정의 프로필 사진. 제공자가 주지 않으면 null */
  socialImageUrl: string | null;
  useSocialImage: boolean;
  name: string;
  onChange: (next: { name?: string; useSocialImage?: boolean }) => void;
  onNext: () => void;
  /** 사진 · 이름 없이(기본 이미지, 이름 없음) 다음으로 */
  onSkip: () => void;
}) {
  const label = SOCIAL_PROVIDER_LABEL[provider];
  const error = nameError(name);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!error) onNext();
  };

  return (
    <SignupScreen
      frame={frame}
      heading={
        <>
          프로필을
          <br />
          꾸며 볼까요?
        </>
      }
      description="사진과 이름은 나중에 정해도 괜찮아요."
      onSubmit={submit}
      footer={
        <>
          <button type="submit" disabled={!!error} className={pillButtonClass}>
            다음
          </button>
          <button type="button" onClick={onSkip} className="mt-2 h-12 w-full text-[15px] font-bold text-ink-sub">
            건너뛰기
          </button>
        </>
      }
    >
      <div className="flex flex-col items-center gap-2.5">
        <Avatar src={useSocialImage ? socialImageUrl : null} name={name.trim() || nickname} size={96} />
        {socialImageUrl ? (
          <button
            type="button"
            onClick={() => onChange({ useSocialImage: !useSocialImage })}
            className="rounded-full border border-line px-3.5 py-1.5 text-[13px] font-bold text-ink active:bg-fill"
          >
            {useSocialImage ? "기본 이미지로 바꾸기" : `${label} 프로필 사진 쓰기`}
          </button>
        ) : (
          <span className="text-[13px] text-ink-sub">기본 이미지로 시작해요</span>
        )}
        <span className="text-[12px] text-ink-faint">사진 업로드는 업로드 API 연결 후 제공돼요</span>
      </div>

      <AuthField
        label={
          <>
            이름 <span className="font-normal text-ink-faint">(선택)</span>
          </>
        }
        htmlFor="signup-name"
        error={error}
        hint="다른 사람과 같아도 돼요. 비워 두면 닉네임이 대신 보여요."
      >
        <AuthInput
          id="signup-name"
          autoComplete="name"
          placeholder="이름을 입력해 주세요"
          maxLength={NAME_MAX}
          value={name}
          onChange={(e) => onChange({ name: e.target.value })}
          invalid={!!error}
          aria-describedby={error ? "signup-name-error" : undefined}
        />
      </AuthField>
    </SignupScreen>
  );
}
