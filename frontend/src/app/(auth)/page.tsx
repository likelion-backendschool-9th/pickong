import { MapPin } from "lucide-react";
import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { pillButtonClass } from "./_components/AuthUi";

/** 시작 화면: 로그인 전 첫 화면. 시작하기를 누르면 로그인으로 이동합니다. */
export default function StartPage() {
  return (
    <div className="flex min-h-dvh flex-col px-5">
      <div className="flex flex-1 flex-col items-center justify-center pt-16 text-center">
        <div className="flex size-28 items-center justify-center rounded-full bg-soft" aria-hidden>
          <div className="flex size-16 items-center justify-center rounded-full bg-selected">
            <MapPin size={34} strokeWidth={2} className="text-primary-deep" />
          </div>
        </div>
        <h1 className="mt-8 text-[44px] leading-none font-black tracking-[-1.5px] text-ink">찜콩</h1>
        <p className="mt-5 text-[20px] font-bold text-ink">가고 싶은 곳, 잊지 않도록</p>
        <p className="mt-2.5 text-[15px] leading-relaxed text-ink-sub">
          발견한 장소를 찜콩하고
          <br />내 지도와 일정으로 한 번에 모아보세요.
        </p>
      </div>
      <div className="pt-8 pb-[calc(32px+env(safe-area-inset-bottom))]">
        <Link href={routes.login()} className={pillButtonClass}>
          시작하기
        </Link>
      </div>
    </div>
  );
}
