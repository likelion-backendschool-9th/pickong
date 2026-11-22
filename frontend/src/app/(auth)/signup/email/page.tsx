import type { Metadata } from "next";
import { SignupFlow } from "../_components/SignupFlow";

export const metadata: Metadata = { title: "이메일로 회원가입" };

export default function EmailSignupPage() {
  return <SignupFlow method="email" />;
}
