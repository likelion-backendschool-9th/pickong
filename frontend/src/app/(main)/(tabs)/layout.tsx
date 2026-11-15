import { Suspense } from "react";
import { BottomTabBar } from "../_components/BottomTabBar";

/** 주요 탭 화면: 하단 내비게이션 고정 + 본문이 가려지지 않도록 하단 여백 확보 */
export default function TabsLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <div className="pb-[calc(76px+env(safe-area-inset-bottom))]">{children}</div>
      {/* 현재 경로(usePathname)는 요청 시점 값이라 Suspense 안에서 읽습니다. */}
      <Suspense
        fallback={
          <div className="fixed inset-x-0 bottom-0 z-40 mx-auto h-[calc(60px+env(safe-area-inset-bottom))] w-full max-w-[480px] border-t border-line bg-bg" />
        }
      >
        <BottomTabBar />
      </Suspense>
    </>
  );
}
