"use client";

export default function GlobalError({ reset }: { error: Error; reset: () => void }) {
  return (
    <html lang="ko">
      <body style={{ fontFamily: "system-ui", padding: 24 }}>
        <h1 style={{ fontSize: 18 }}>문제가 발생했어요</h1>
        <p style={{ color: "#5d6357", fontSize: 14 }}>잠시 후 다시 시도해 주세요.</p>
        <button onClick={reset} style={{ marginTop: 16, height: 44, padding: "0 16px" }}>
          다시 시도
        </button>
      </body>
    </html>
  );
}
