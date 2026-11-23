import type { NextConfig } from "next";

/**
 * /api/* 요청은 백엔드로 전달합니다.
 * 개발 중에는 mock 서버(localhost:4000), 백엔드가 준비되면 API_BASE_URL만 바꿉니다.
 */
const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:4000";

const nextConfig: NextConfig = {
  output: "standalone",
  cacheComponents: true,
  devIndicators: false,
  partialPrefetching: true,
  images: {
    // 온보딩에서 기본값으로 보여 주는 소셜 계정 프로필 사진 (카카오 · Google)
    remotePatterns: [
      { protocol: "https", hostname: "*.kakaocdn.net" },
      { protocol: "https", hostname: "lh3.googleusercontent.com" },
    ],
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
