import type { Metadata } from "next";
import { SignupFlow } from "./_components/SignupFlow";

export const metadata: Metadata = { title: "회원가입" };

/** 기본 가입 방법: 전화번호로 회원가입. 첫 단계 하단에서 이메일 가입으로 바꿀 수 있습니다. */
export default function SignupPage() {
  return <SignupFlow method="phone" />;
}
