import Image from "next/image";
import type { SocialProvider } from "./socialProviders";

/** 제공자 로고. 각 사의 로고 색을 그대로 써야 하므로 색상 토큰 대신 public/icons의 SVG 파일을 씁니다. */
const LOGO_SRC: Record<SocialProvider, string> = {
  KAKAO: "/icons/kakao.svg",
  GOOGLE: "/icons/google.svg",
};

export function SocialLogo({ provider, size = 20 }: { provider: SocialProvider; size?: number }) {
  return <Image src={LOGO_SRC[provider]} alt="" width={size} height={size} unoptimized aria-hidden />;
}
