/**
 * 공유하기: 기기 공유 시트가 있으면 열고, 없으면 내용을 클립보드에 복사합니다.
 * 사용자가 공유를 취소하면 "cancelled".
 */
export async function shareOrCopy(data: { title: string; text?: string; url?: string }): Promise<"shared" | "copied" | "cancelled"> {
  try {
    if (navigator.share) {
      await navigator.share(data);
      return "shared";
    }
    await navigator.clipboard.writeText([data.text, data.url].filter(Boolean).join("\n"));
    return "copied";
  } catch {
    return "cancelled";
  }
}
