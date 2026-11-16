import type { Metadata, Viewport } from "next";
import { IBM_Plex_Sans_KR, Inter, Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

const noto = Noto_Sans_KR({
  variable: "--font-noto",
  // 900: ‘찜콩’ 워드마크
  weight: ["400", "500", "700", "900"],
  subsets: ["latin"],
  display: "swap",
});

/** 기능 제목(내 지도 · 메시지 등) */
const plex = IBM_Plex_Sans_KR({
  variable: "--font-plex",
  weight: ["500"],
  subsets: ["latin"],
  display: "swap",
});

/** 영문 라벨(MY MAP 등) · 닉네임 */
const inter = Inter({
  variable: "--font-inter",
  weight: ["600"],
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: "찜콩 Pickong", template: "%s · 찜콩" },
  description: "당신의 마침표. 누군가의 \"따옴표\"가 됩니다 — 사진으로 발견하고, 내 지도에 저장하고, 하루를 계획하는 소셜 지도",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#ffffff",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${noto.variable} ${plex.variable} ${inter.variable} antialiased`}>
      <body className="bg-fill">
        <Providers>
          {/* 모바일 콘텐츠 열: 360~480px은 가로폭에 맞추고, 데스크톱에서는 가운데 배치 */}
          <div className="relative mx-auto min-h-dvh w-full max-w-[480px] bg-bg min-[481px]:shadow-[0_0_0_1px_var(--color-line)]">
            {children}
          </div>
        </Providers>
      </body>
    </html>
  );
}
