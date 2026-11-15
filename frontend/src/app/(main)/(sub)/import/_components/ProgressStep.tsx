import { Check } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Button } from "@/shared/ui/Button";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";

const STEPS = [
  { title: "콘텐츠 확인", desc: "공개 페이지인지 확인해요" },
  { title: "장소명과 주소 찾기", desc: "장소 후보를 찾고 있어요" },
  { title: "지도 위치 비교", desc: "정확한 위치를 확인하고 있어요" },
  { title: "결과 정리", desc: "확인이 끝난 장소부터 보여드려요" },
];

export function ProgressStep({ step, total, onCancel }: { step: number; total: number; onCancel: () => void }) {
  const percent = Math.round((Math.min(step, total) / total) * 100);
  return (
    <Screen>
      <TopBar title="장소를 찾고 있어요" hideBack />
      <div className="px-5 pt-2">
        <h2 className="text-[22px] leading-snug font-bold">
          확인한 장소부터
          <br />
          차근차근 모을게요
        </h2>
        <div className="mt-6 h-1.5 overflow-hidden rounded-full bg-fill" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${Math.max(8, percent)}%` }} />
        </div>
        <ol className="mt-8 space-y-6" aria-live="polite">
          {STEPS.map((s, i) => {
            const done = i < step;
            const active = i === step;
            return (
              <li key={s.title} className="flex gap-3">
                <span
                  className={cn(
                    "flex size-6 shrink-0 items-center justify-center rounded-full text-[12px] font-bold",
                    done ? "bg-selected text-primary-deep" : active ? "bg-primary text-on-primary" : "bg-fill text-ink-faint",
                  )}
                >
                  {done ? <Check size={14} strokeWidth={3} /> : active ? "…" : i + 1}
                </span>
                <div>
                  <p className={cn("text-[14px] font-bold", !done && !active && "text-ink-faint")}>{s.title}</p>
                  <p className="text-[12px] text-ink-sub">{s.desc}</p>
                </div>
              </li>
            );
          })}
        </ol>
      </div>
      <BottomCta>
        <p className="mb-2 text-center text-[12px] text-ink-faint">진행 중 가져오기를 취소할 수 있어요.</p>
        <Button block variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </BottomCta>
    </Screen>
  );
}
