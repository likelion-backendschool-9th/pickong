import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { userKeys } from "@/entities/user";
import { ApiError } from "@/shared/api/errors";
import { http } from "@/shared/api/http";
import { parseSocialProvider, type SocialProvider } from "@/shared/auth/socialProviders";
import type { TermsType } from "../_lib/signup";

/*
 * 최초 가입 온보딩 API. 소셜 인증 직후 서버가 내려준 임시 가입 세션(httpOnly 쿠키)으로만 호출할 수 있고,
 * 이 세션으로는 다른 서비스 API를 쓸 수 없습니다. 가입을 마치면 서버가 정식 인증 쿠키를 내려줍니다.
 */

interface SignupSessionDto {
  provider: string;
  /** 소셜 계정의 이름 · 프로필 사진 · 이메일 (제공자가 주지 않으면 null) */
  name: string | null;
  profileImageUrl: string | null;
  email: string | null;
}

export interface SignupSession {
  provider: SocialProvider;
  name: string | null;
  profileImageUrl: string | null;
  email: string | null;
}

export interface CompleteSignupInput {
  /** 아이디(영문, 중복 불가) → users.username */
  username: string;
  /** 이름(중복 가능) → users.display_name. 정하지 않으면 null */
  name: string | null;
  /** true면 소셜 계정의 프로필 사진을 쓰고, false면 기본 이미지로 시작합니다. 사진 주소는 서버가 가진 값만 씁니다. */
  useSocialProfileImage: boolean;
  agreedTerms: TermsType[];
}

const signupKeys = {
  session: ["auth", "signup"] as const,
};

/**
 * 진행 중인 가입 정보. 새로고침하거나 나갔다 와도 서버의 가입 세션이 기준이라 상태가 어긋나지 않습니다.
 * - 401 SIGNUP_SESSION_EXPIRED: 가입 세션이 없거나 만료됨 → 로그인 화면으로
 * - 409 ALREADY_REGISTERED: 이미 가입을 마치고 로그인한 상태 → 피드로
 */
export const useSignupSession = () =>
  useQuery({
    queryKey: signupKeys.session,
    queryFn: async () => {
      const dto = await http.get<SignupSessionDto>("/auth/signup");
      const provider = parseSocialProvider(dto.provider);
      if (!provider) throw new ApiError(500, "UNKNOWN_PROVIDER", "지원하지 않는 로그인 방식이에요.");
      return { ...dto, provider } satisfies SignupSession;
    },
    staleTime: Infinity,
    // 화면을 떠나면 바로 버립니다. 가입을 마치거나 그만둔 뒤 다시 들어오면 서버에 새로 물어봅니다.
    gcTime: 0,
    retry: false,
  });

/** 가입 완료. 성공하면 서버가 임시 가입 세션을 지우고 Access · Refresh Token 쿠키를 내려줍니다. */
export function useCompleteSignup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CompleteSignupInput) => http.post<{ username: string }>("/auth/signup", input),
    onError: (error, input) => {
      // 확인 뒤 다른 사람이 먼저 가져간 경우: 입력칸 옆 표시도 ‘사용 중’으로 바꿉니다.
      if (error instanceof ApiError && error.code === "USERNAME_TAKEN") {
        qc.setQueryData(userKeys.nicknameAvailability(input.username), { available: false });
      }
    },
  });
}

/** 가입 그만두기: 서버의 임시 가입 세션을 지웁니다. 다음에 같은 소셜 계정으로 로그인하면 처음부터 다시 진행합니다. */
export const useCancelSignup = () => useMutation({ mutationFn: () => http.delete<void>("/auth/signup") });
