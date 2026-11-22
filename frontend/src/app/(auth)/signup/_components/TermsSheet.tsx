"use client";

import Link from "next/link";
import { routes } from "@/shared/config/routes";
import { BottomSheet } from "@/shared/ui/BottomSheet";
import { AuthCheckbox, pillButtonClass } from "../../_components/AuthUi";
import { AGREEMENT_ITEMS, hasRequiredAgreements, isAllAgreed, toggleAllAgreements, type Agreements } from "../_lib/signup";

/**
 * 4단계: 닉네임 화면 위로 올라오는 약관 동의 시트. 필수 약관에 동의해야 시작할 수 있습니다.
 * ‘보기’는 새 탭으로 열어, 약관을 읽고 돌아와도 지금까지 입력한 가입 정보가 남아 있게 합니다.
 */
export function TermsSheet({
  open,
  agreements,
  onChange,
  onClose,
  onConfirm,
}: {
  open: boolean;
  agreements: Agreements;
  onChange: (agreements: Agreements) => void;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="약관에 동의해 주세요"
      description="필수 약관에 동의하면 찜콩을 시작할 수 있어요."
      footer={
        <button type="button" disabled={!hasRequiredAgreements(agreements)} onClick={onConfirm} className={pillButtonClass}>
          동의하고 시작하기
        </button>
      }
    >
      <fieldset>
        <legend className="sr-only">약관 동의</legend>
        <div className="rounded-[14px] bg-soft px-5 py-4">
          <AuthCheckbox
            className="text-[16px] font-bold text-ink"
            checked={isAllAgreed(agreements)}
            onChange={() => onChange(toggleAllAgreements(agreements))}
          >
            약관에 모두 동의해요
          </AuthCheckbox>
        </div>
        <ul className="mt-3 space-y-1 px-1">
          {AGREEMENT_ITEMS.map((item) => (
            <li key={item.key} className="flex items-center justify-between gap-3 py-1">
              <AuthCheckbox
                className="text-[15px] text-ink-sub"
                checked={agreements[item.key]}
                onChange={(checked) => onChange({ ...agreements, [item.key]: checked })}
              >
                [{item.required ? "필수" : "선택"}] {item.label}
              </AuthCheckbox>
              <Link
                href={routes.terms(item.termsType)}
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 px-1 py-1 text-[15px] text-ink-sub underline underline-offset-2"
                aria-label={`${item.label} 보기 (새 탭)`}
              >
                보기
              </Link>
            </li>
          ))}
        </ul>
      </fieldset>
    </BottomSheet>
  );
}
