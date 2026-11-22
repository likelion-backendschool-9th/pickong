import type { Metadata } from "next";
import { routes } from "@/shared/config/routes";
import { TopBar } from "@/shared/ui/layout";
import { LoginForm } from "./_components/LoginForm";

export const metadata: Metadata = { title: "로그인" };

export default function LoginPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="로그인" backHref={routes.start()} />
      <LoginForm />
    </div>
  );
}
