import { Globe, Users, type LucideProps } from "lucide-react";
import type { SVGProps } from "react";
import { visibilityLabel, type Visibility } from "@/shared/config/visibility";

type IconProps = Omit<LucideProps, "ref">;

/** 닫힌 자물쇠: 몸통을 채워 작은 크기에서도 잠긴 모양으로 보이게 합니다 (비공개 · 유료 잠금 등). */
export function ClosedLock({ size = 24, strokeWidth = 2, className, ...props }: IconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...(props as SVGProps<SVGSVGElement>)}
    >
      <path d="M7 12V7a5 5 0 0 1 10 0v5" />
      <rect x="4" y="11" width="16" height="10" rx="2" fill="currentColor" />
    </svg>
  );
}

const ICONS = { PUBLIC: Globe, FOLLOWERS: Users, PRIVATE: ClosedLock } as const;

/** 공개 범위 아이콘: 전체 공개(지구) · 팔로워 공개(사람들) · 비공개(닫힌 자물쇠) */
export function VisibilityIcon({ visibility, ...props }: { visibility: Visibility } & IconProps) {
  const Icon = ICONS[visibility];
  return <Icon role="img" aria-label={visibilityLabel(visibility)} {...props} />;
}
