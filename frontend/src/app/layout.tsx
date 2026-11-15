import type { Metadata, Viewport } from "next";
import { Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

const noto = Noto_Sans_KR({
  variable: "--font-noto",
  weight: ["400", "500", "700"],
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
    <html lang="ko" className={`${noto.variable} antialiased`}>
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
