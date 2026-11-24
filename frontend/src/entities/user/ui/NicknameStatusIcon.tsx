import { CircleCheck, CircleX, LoaderCircle } from "lucide-react";
import type { NicknameStatus } from "../nickname";

/** 닉네임 입력칸 오른쪽 표시: 확인 중(회색 원) · 사용 가능(초록 체크) · 사용 불가(빨간 X). 부모는 relative여야 합니다. */
export function NicknameStatusIcon({ status }: { status: NicknameStatus }) {
  const base = "pointer-events-none absolute top-1/2 right-4 -translate-y-1/2";
  if (status === "checking") return <LoaderCircle size={22} className={`${base} animate-spin text-ink-faint`} aria-hidden />;
  if (status === "available") return <CircleCheck size={22} className={`${base} text-primary-deep`} aria-hidden />;
  if (status === "invalid" || status === "taken") return <CircleX size={22} className={`${base} text-danger`} aria-hidden />;
  return null;
}
