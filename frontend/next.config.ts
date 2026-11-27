import type { NextConfig } from "next";

/**
 * 브라우저의 /api/* 요청을 어디로 보낼지 정합니다. 둘 다 서버 시작(배포는 빌드) 시 읽는 환경 변수입니다.
 * - API_BASE_URL: 기본 목적지. 개발은 mock 서버(localhost:4000), 배포는 백엔드 주소
 * - BACKEND_URL: 개발 중에만 씁니다. 정하면 BACKEND_PATHS의 경로만 이 백엔드(예: localhost:8080)로 보내고,
 *   나머지(아직 백엔드에 없는 API)는 API_BASE_URL(mock)로 보냅니다. 정하지 않으면 전부 API_BASE_URL로 갑니다.
 */
const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:4000";
const backendUrl = process.env.BACKEND_URL;

/**
 * 백엔드에 구현된 API. 백엔드에 새 API가 생기면 여기에 경로를 추가합니다.
 * 쿠키를 주고받는 소셜 로그인 시작 · 콜백도 /api/v1/auth 아래라 같이 백엔드로 갑니다.
 */
const BACKEND_PATHS = [
  "/api/v1/auth/:path*",
  "/api/v1/me",
  "/api/v1/me/social-accounts/:path*",
  "/api/v1/terms/:path*",
  "/api/v1/me/agreements",
  "/api/v1/me/agreements/:path*",
  "/api/v1/me/withdrawal",
  "/api/v1/admin/users/:path*",
  "/api/v1/admin/feeds/:path*",
  "/api/v1/admin/reports/:path*",
  // 프로필 상단(사용자 정보)과 컬렉션 탭. 게시물 · 방문 탭은 아직 mock입니다.
  "/api/v1/users/:username",
  "/api/v1/users/:username/collections",
  // 장소 제보 화면은 관리자 로그인이 없어 mock에 둡니다. 백엔드로 보내면 목록이 비거나 로그인으로 이동합니다.
];

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
    // 위에서부터 처음 맞는 규칙이 적용됩니다.
    const toBackend = backendUrl
      ? BACKEND_PATHS.map((source) => ({ source, destination: `${backendUrl}${source}` }))
      : [];
    return [...toBackend, { source: "/api/:path*", destination: `${apiBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
