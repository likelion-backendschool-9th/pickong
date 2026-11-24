/**
 * 네이버 지도 GL · Style Editor 설정.
 * `customStyleId`는 GL에서만 동작하므로 Style ID가 있으면 GL을 켭니다.
 */
export function getNaverMapStyleId(): string | undefined {
  const id = process.env.NEXT_PUBLIC_NAVER_MAP_STYLE_ID?.trim();
  return id || undefined;
}

/** Style ID가 있거나 NEXT_PUBLIC_NAVER_MAP_GL=true 이면 GL 벡터맵 사용 */
export function shouldUseNaverGl(): boolean {
  if (getNaverMapStyleId()) return true;
  const flag = process.env.NEXT_PUBLIC_NAVER_MAP_GL?.trim().toLowerCase();
  return flag === "1" || flag === "true";
}

export function naverMapGlOptions(): { gl?: true; customStyleId?: string } {
  const styleId = getNaverMapStyleId();
  if (styleId) return { gl: true, customStyleId: styleId };
  if (shouldUseNaverGl()) return { gl: true };
  return {};
}
