"use client";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { routes } from "@/shared/config/routes";

const rows = [
  { label: "프로필 편집", desc: "닉네임 · 소개 · 활동 지역", href: routes.editProfile() },
  { label: "기본 공개 범위", desc: "새 게시물 · 컬렉션의 기본값 · 전체 공개", href: null },
  { label: "알림 설정", desc: "소셜 · 일정 점검 알림", href: null },
  { label: "위치 권한", desc: "직접 기능을 쓸 때만 사용해요 · 실시간 위치를 공개하지 않아요", href: null },
  { label: "내 데이터와 계정", desc: "저장 데이터 내보내기 · 계정 탈퇴", href: null },
];

/** 계정 설정 목록: 설정 페이지와 프로필의 메뉴 패널이 함께 씁니다. */
export function SettingsMenu({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <>
      <ul className="divide-y divide-line px-5">
        {rows.map((r) => {
          const body = (
            <>
              <span className="min-w-0 flex-1">
                <span className="block text-[15px] font-bold">{r.label}</span>
                <span className="block text-[12px] text-ink-sub">{r.desc}</span>
              </span>
              {r.href ? <ChevronRight size={18} className="text-ink-faint" /> : <span className="text-[11px] text-ink-faint">준비 중</span>}
            </>
          );
          return (
            <li key={r.label}>
              {r.href ? (
                <Link href={r.href} onClick={onNavigate} className="flex min-h-16 items-center gap-3 py-3">
                  {body}
                </Link>
              ) : (
                <div className="flex min-h-16 items-center gap-3 py-3" aria-disabled="true">
                  {body}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      <p className="px-5 pt-6 pb-4 text-[12px] text-ink-faint">로그인 · 로그아웃은 백엔드 인증 연결 후 제공돼요.</p>
    </>
  );
}
