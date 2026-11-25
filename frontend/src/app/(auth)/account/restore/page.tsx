import type { Metadata } from "next";
import { RestoreScreen } from "./RestoreScreen";

export const metadata: Metadata = { title: "탈퇴 취소" };

/** 탈퇴 취소 (탈퇴 취소 쿠키 필요, 로그인 세션 없음). 탈퇴 유예 중인 회원이 로그인하면 서버가 보냅니다. */
export default function AccountRestorePage() {
  return <RestoreScreen />;
}
