"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { authApi } from "@/shared/auth/authApi";
import { routes } from "@/shared/config/routes";

interface Row {
  label: string;
  desc: string;
  href: string | null;
}

const sections: { title: string; rows: Row[] }[] = [
  {
    title: "계정",
    rows: [
      { label: "프로필 편집", desc: "닉네임 · 이름 · 소개 · 활동 지역", href: routes.editProfile() },
      { label: "로그인 수단 관리", desc: "카카오 · Google 계정 연결 · 해제", href: routes.loginMethods() },
      { label: "약관 및 동의 관리", desc: "필수 약관 확인 · 마케팅 수신 동의", href: routes.agreements() },
    ],
  },
  {
    title: "알림",
    rows: [
      { label: "푸시 알림 설정", desc: "소셜 · 일정 · 혜택 알림, 방해 금지 시간", href: routes.notificationSettings() },
      { label: "디바이스 관리", desc: "로그인한 기기 · 기기별 푸시", href: routes.devices() },
    ],
  },
  {
    title: "내 장소",
    rows: [
      { label: "내 태그", desc: "태그로 저장한 장소 찾기", href: routes.tags() },
      { label: "직접 등록한 장소", desc: "지도에서 직접 만든 장소 수정 · 삭제", href: routes.myPlaces() },
    ],
  },
  {
    title: "결제 · 크리에이터",
    rows: [
      { label: "구매 내역", desc: "유료 컬렉션 결제 · 환불 신청", href: routes.purchases() },
      { label: "크리에이터", desc: "신청 · 심사 결과 · 정산 계좌 · 수익", href: routes.creator() },
    ],
  },
  {
    title: "기타",
    rows: [
      { label: "기본 공개 범위", desc: "새 게시물 · 컬렉션의 기본값 · 전체 공개", href: null },
      { label: "위치 권한", desc: "직접 기능을 쓸 때만 사용해요 · 실시간 위치를 공개하지 않아요", href: null },
    ],
  },
];

/** 계정 설정 목록: 설정 페이지와 프로필의 메뉴 패널이 함께 씁니다. */
export function SettingsMenu({ onNavigate }: { onNavigate?: () => void }) {
  const router = useRouter();
  const qc = useQueryClient();
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      onNavigate?.();
      router.replace(routes.start());
      // 다른 계정으로 다시 로그인했을 때 이전 계정의 응답이 보이지 않도록 캐시를 비웁니다.
      qc.clear();
    },
  });

  return (
    <>
      {sections.map((section) => (
        <section key={section.title} className="pt-3">
          <h3 className="px-5 pb-1 text-[12px] font-bold text-ink-faint">{section.title}</h3>
          <ul className="divide-y divide-line px-5">
            {section.rows.map((r) => {
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
        </section>
      ))}
      <div className="mt-4 flex items-center gap-4 border-t border-line px-5 pt-4 pb-6 text-[13px]">
        <button type="button" onClick={() => logout.mutate()} disabled={logout.isPending} className="font-bold text-ink">
          {logout.isPending ? "로그아웃 중…" : "로그아웃"}
        </button>
        <span className="h-3 w-px bg-line" aria-hidden />
        <Link href={routes.withdraw()} onClick={onNavigate} className="text-ink-faint">
          회원 탈퇴
        </Link>
      </div>
    </>
  );
}
