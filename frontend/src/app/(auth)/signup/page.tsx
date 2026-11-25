import type { Metadata } from "next";
import { SignupFlow } from "./_components/SignupFlow";

export const metadata: Metadata = { title: "회원가입" };

/**
 * 최초 가입 온보딩. 카카오 · Google 인증을 마친 신규 회원만 서버가 이 화면으로 보냅니다.
 * 아이디(영문, 중복 확인) → 프로필 사진 · 이름(선택, 건너뛰기) → 약관 동의 순서이며, 비밀번호 · 전화번호는 받지 않습니다.
 */
export default function SignupPage() {
  return <SignupFlow />;
}
