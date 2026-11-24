import { Heart } from "lucide-react";

/** 지도 핀: (0,0)이 핀 끝. 큰 핀(main)은 안에 하트를 넣어 ‘찜’을 표시합니다. */
function Pin({ x, y, scale = 1, tone, main = false }: { x: number; y: number; scale?: number; tone: string; main?: boolean }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      <ellipse cx={0} cy={1.5} rx={7} ry={2.4} fill="var(--color-ink)" opacity={0.14} />
      <path
        d="M0 0 C -8 -11 -14 -18 -14 -26 A14 14 0 1 1 14 -26 C 14 -18 8 -11 0 0 Z"
        fill={tone}
        stroke="var(--color-bg)"
        strokeWidth={2.5}
        strokeLinejoin="round"
      />
      <circle cx={0} cy={-26} r={main ? 8 : 5} fill="var(--color-bg)" />
      {main && (
        <path
          d="M0 -22 C -4.4 -25 -5.6 -26.8 -5.6 -28.4 A2.8 2.8 0 0 1 0 -29.6 A2.8 2.8 0 0 1 5.6 -28.4 C 5.6 -26.8 4.4 -25 0 -22 Z"
          fill="var(--color-primary-deep)"
        />
      )}
    </g>
  );
}

/**
 * 로그인 화면 위쪽 그림: 내 지도에 찜해 둔 장소들과 그 사이 동선.
 * 지도 색은 Map.tsx와 같은 map-* 토큰, 핀 · 동선은 브랜드 연두를 씁니다. 장식이라 스크린 리더에서는 숨깁니다.
 */
export function LoginHero() {
  return (
    <div className="relative aspect-[350/250] max-h-[260px] w-full overflow-hidden rounded-[28px] bg-map-land ring-1 ring-line short:max-h-[168px]" aria-hidden>
      <svg viewBox="0 0 350 250" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 size-full">
        {/* 공원 · 강 */}
        <rect x={198} y={22} width={124} height={84} rx={18} fill="var(--color-map-park)" />
        <rect x={20} y={150} width={92} height={60} rx={14} fill="var(--color-map-park)" opacity={0.8} />
        <path d="M-10 212 C 60 194 120 234 190 220 S 300 192 360 206 V 260 H -10 Z" fill="var(--color-map-water)" />

        {/* 도로: 테두리(line) 위에 흰 길 */}
        <g fill="none" strokeLinecap="round" stroke="var(--color-line)" strokeWidth={17}>
          <path d="M-10 124 H360" />
          <path d="M150 -10 V200" />
        </g>
        <g fill="none" strokeLinecap="round" stroke="var(--color-map-road)">
          <path d="M-10 124 H360" strokeWidth={12} />
          <path d="M150 -10 V200" strokeWidth={12} />
          <path d="M-10 58 H138" strokeWidth={6} />
          <path d="M162 176 H360" strokeWidth={6} />
          <path d="M262 -10 V112" strokeWidth={6} />
        </g>

        {/* 찜한 장소를 잇는 동선 */}
        <path
          d="M84 82 C 112 96 132 116 172 146 S 246 160 264 70"
          fill="none"
          stroke="var(--color-primary-deep)"
          strokeWidth={3}
          strokeLinecap="round"
          strokeDasharray="1 8"
        />

        <Pin x={84} y={86} scale={0.9} tone="var(--color-primary-deep)" />
        <Pin x={264} y={74} scale={0.9} tone="var(--color-primary-deep)" />
        <circle cx={306} cy={150} r={6} fill="var(--color-bg)" stroke="var(--color-primary)" strokeWidth={3} />
        <Pin x={172} y={150} scale={1.45} tone="var(--color-primary)" main />
      </svg>

      <span className="absolute top-4 left-4 rounded-full bg-bg/90 px-2.5 py-1 font-latin text-[11px] tracking-[0.12em] text-primary-deep short:hidden">
        MY MAP
      </span>
      <div className="absolute bottom-4 left-4 flex items-center gap-2 rounded-full bg-bg/95 py-1.5 pr-3.5 pl-1.5 shadow-lg shadow-ink/10 short:hidden">
        <span className="flex size-7 items-center justify-center rounded-full bg-primary">
          <Heart size={14} strokeWidth={2.4} className="fill-on-primary text-on-primary" />
        </span>
        <span className="text-[13px] font-bold text-ink">카페 온유를 찜콩했어요</span>
      </div>
    </div>
  );
}
