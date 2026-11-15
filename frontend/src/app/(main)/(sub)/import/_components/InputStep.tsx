"use client";

import { ClipboardPaste } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "@/shared/api/errors";
import { routes } from "@/shared/config/routes";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/display";
import { Field, Input, Textarea } from "@/shared/ui/form";
import { BottomCta, Screen, TopBar } from "@/shared/ui/layout";

export function InputStep({
  mode,
  failed,
  onMode,
  onSubmit,
  submitting,
  error,
}: {
  mode: "url" | "text";
  failed?: string;
  onMode: (mode: "url" | "text") => void;
  onSubmit: (input: { url: string } | { text: string }) => void;
  submitting: boolean;
  error: unknown;
}) {
  const [url, setUrl] = useState("");
  const [text, setText] = useState("");

  const paste = async () => {
    try {
      setUrl((await navigator.clipboard.readText()).trim());
    } catch {
      /* 권한 없음 */
    }
  };

  if (mode === "text") {
    return (
      <Screen>
        <TopBar title="텍스트로 가져오기" onBack={() => onMode("url")} />
        <div className="space-y-5 px-5 pt-2 pb-6">
          <div>
            <h2 className="text-[22px] leading-snug font-bold">{failed ? "링크를 열 수 없어요" : "텍스트를 붙여넣어 주세요"}</h2>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-sub">
              {failed ? `${failed} 장소가 적힌 텍스트를 붙여넣어 주세요.` : "글에서 장소 이름을 찾아 위치를 확인해요."}
            </p>
          </div>
          <Field label="콘텐츠 텍스트" htmlFor="import-text">
            <Textarea
              id="import-text"
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={"예) 성수에서 들른 카페 온유와 소품샵 모모,\n그리고 노을 보러 간 전망대까지"}
              className="min-h-44"
            />
          </Field>
          <p className="text-[12px] text-ink-faint">직접 입력한 텍스트도 위치 확인 후 저장해요.</p>
          {!!error && <Notice tone="danger">{errorMessage(error)}</Notice>}
        </div>
        <BottomCta className="space-y-2">
          <Button block disabled={!text.trim()} loading={submitting} onClick={() => onSubmit({ text })}>
            입력한 텍스트에서 장소 찾기
          </Button>
          <Button block variant="secondary" onClick={() => onMode("url")}>
            다른 링크 입력
          </Button>
        </BottomCta>
      </Screen>
    );
  }

  return (
    <Screen>
      <TopBar title="링크로 장소 가져오기" backHref={routes.map()} />
      <div className="space-y-6 px-5 pt-2 pb-6">
        <div>
          <h2 className="text-[22px] leading-snug font-bold">
            발견한 장소 모음,
            <br />내 지도에 한 번에
          </h2>
          <p className="mt-2 text-[13px] text-ink-sub">링크를 넣고, 장소를 확인한 뒤 저장하세요.</p>
        </div>
        <Field label="콘텐츠 링크" htmlFor="import-url">
          <div className="relative">
            <Input
              id="import-url"
              type="url"
              inputMode="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://blog.example.com/seongsu"
              className="pr-24"
            />
            <button type="button" onClick={paste} className="absolute top-1/2 right-2 flex h-9 -translate-y-1/2 items-center gap-1 rounded-md px-2 text-[12px] font-bold text-primary-deep">
              <ClipboardPaste size={14} /> 붙여넣기
            </button>
          </div>
        </Field>
        {!!error && <Notice tone="danger">{errorMessage(error)}</Notice>}
        <Button block disabled={!url.trim()} loading={submitting} onClick={() => onSubmit({ url: url.trim() })}>
          장소 가져오기
        </Button>

        <div className="rounded-card bg-soft p-4">
          <h3 className="text-[13px] font-bold">가져올 수 있는 콘텐츠</h3>
          <p className="mt-1.5 text-[12px] leading-relaxed text-ink-sub">
            누구나 열 수 있는 블로그 글과 공개 웹페이지의 장소를 찾아요. 로그인이 필요하거나 비공개인 콘텐츠는 분석하지 못할 수 있어요.
          </p>
          <button type="button" onClick={() => onMode("text")} className="mt-3 text-[12px] font-bold text-primary-deep">
            링크 접근이 안 되면 텍스트를 직접 입력할 수 있어요 →
          </button>
        </div>
        <p className="text-[11px] text-ink-faint">예시: 주소에 ‘private’이 들어가면 접근 실패 화면을 확인할 수 있어요.</p>
      </div>
    </Screen>
  );
}
