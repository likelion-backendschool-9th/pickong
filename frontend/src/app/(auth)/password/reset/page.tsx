import type { Metadata } from "next";
import { PasswordResetFlow } from "./_components/PasswordResetFlow";

export const metadata: Metadata = { title: "비밀번호 재설정" };

export default function PasswordResetPage() {
  return <PasswordResetFlow />;
}
