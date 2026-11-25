import { HttpError, notFound, type Route } from "../router.ts";

/* 계정 관리 · 약관 · 알림 설정 · 디바이스 (mock 메모리 상태) */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

type TermsType = "SERVICE" | "PRIVACY" | "MARKETING";

/** 지금 시행 중인 버전. 백엔드와 같은 문구입니다: SERVICE · PRIVACY는 V12의 1.1, MARKETING은 V8의 1.0 (법률 검토 전 초안) */
const TERMS: Record<TermsType, { title: string; required: boolean; version: string; effectiveDate: string; sections: { heading: string; body: string }[] }> = {
  SERVICE: {
    title: "서비스 이용약관",
    required: true,
    version: "1.1",
    effectiveDate: "2026-11-25",
    sections: [
      { heading: "제1조 (목적)", body: "이 약관은 찜콩(이하 ‘서비스’)이 제공하는 장소 저장 · 공유 · 일정 기능의 이용 조건과 절차, 회원과 서비스의 권리 · 의무를 정합니다." },
      { heading: "제2조 (회원 가입)", body: "회원 가입은 이용자가 카카오 또는 Google 계정으로 로그인하고 약관에 동의한 뒤 가입 신청을 하면 완료됩니다. 한 사람은 하나의 계정만 만들 수 있습니다." },
      { heading: "제3조 (게시물)", body: "회원이 올린 사진 · 후기 · 컬렉션의 권리는 회원에게 있습니다. 다른 사람의 권리를 침해하는 게시물은 신고에 따라 숨김 처리될 수 있습니다." },
      { heading: "제4조 (탈퇴)", body: "회원은 언제든지 설정에서 탈퇴를 신청할 수 있습니다. 신청하면 바로 모든 기기에서 로그아웃되고, 프로필과 게시물이 다른 회원에게 보이지 않습니다. 신청한 때부터 30일 안에 같은 소셜 계정으로 다시 로그인하면 탈퇴를 취소할 수 있습니다. 30일이 지나면 계정 정보와 게시물, 저장한 장소 · 컬렉션 · 일정을 지우며 되돌릴 수 없습니다. 회원이 남긴 댓글은 지우지 않고 작성자를 ‘탈퇴한 사용자’로 표시합니다." },
      { heading: "제5조 (다시 가입)", body: "탈퇴를 신청한 뒤 30일 동안은 같은 소셜 계정으로 새로 가입할 수 없고, 로그인하면 탈퇴를 취소하는 화면으로 이동합니다. 30일이 지나 계정 정보가 지워진 뒤에는 같은 소셜 계정으로 새로 가입할 수 있으며, 이전 계정의 정보와 게시물은 이어지지 않습니다." },
    ],
  },
  PRIVACY: {
    title: "개인정보 수집 및 이용",
    required: true,
    version: "1.1",
    effectiveDate: "2026-11-25",
    sections: [
      { heading: "수집 항목", body: "필수: 소셜 계정 식별자(카카오 · Google 회원번호), 사용자 ID, 소셜 계정에서 인증된 이메일(제공되는 경우)\n선택: 이름, 프로필 사진, 소개, 활동 지역" },
      { heading: "이용 목적", body: "회원 식별, 한 사람 한 계정 확인, 서비스 제공, 부정 이용 방지" },
      { heading: "보관 기간 및 파기", body: "탈퇴를 신청하면 30일 동안 보관한 뒤 파기합니다. 이 기간에 다시 로그인하면 탈퇴를 취소할 수 있습니다. 파기할 때는 이름 · 이메일 · 프로필 사진 · 소개 · 활동 지역 · 소셜 계정 연결을 지우고, 사용자 ID는 알아볼 수 없는 값으로 바꿉니다. 탈퇴 사유는 서비스 개선 통계로만 쓰며, 회원을 알아볼 수 없게 날짜와 함께만 보관합니다. 카카오 · Google 계정 설정의 ‘연결된 앱’에서 찜콩 연결은 회원이 직접 해제할 수 있습니다." },
    ],
  },
  MARKETING: {
    title: "마케팅 정보 수신",
    required: false,
    version: "1.0",
    effectiveDate: "2026-11-01",
    sections: [
      { heading: "내용", body: "새 기능, 이벤트, 추천 컬렉션 소식을 푸시 알림과 이메일로 보내 드립니다." },
      { heading: "철회", body: "설정 > 약관 및 동의 관리에서 언제든지 수신 동의를 철회할 수 있습니다." },
    ],
  },
};

/**
 * 재동의 화면을 mock으로 보려면 MOCK_RECONSENT=PRIVACY,MARKETING처럼 지정합니다.
 * 지정한 약관이 '재동의 필요' 다음 버전(예: 1.1 → 1.2)으로 바뀐 것으로 보고, 다시 동의할 때까지 동의하지 않음으로 둡니다.
 * (서버의 403 TERMS_RECONSENT_REQUIRED 차단은 흉내 내지 않습니다. 백엔드로 확인하세요)
 */
const reconsent = new Set(
  (process.env.MOCK_RECONSENT ?? "")
    .split(",")
    .map((type) => type.trim().toUpperCase())
    .filter((type): type is TermsType => type === "SERVICE" || type === "PRIVACY" || type === "MARKETING"),
);
/** 다음 버전 번호 (1.1 → 1.2) */
const nextVersion = (version: string) => {
  const [major, minor = "0"] = version.split(".");
  return `${major}.${Number(minor) + 1}`;
};
for (const type of reconsent) TERMS[type].version = nextVersion(TERMS[type].version);

const agreements: Record<TermsType, { agreed: boolean; agreedAt: string | null }> = {
  SERVICE: { agreed: true, agreedAt: ago(40) },
  PRIVACY: { agreed: true, agreedAt: ago(40) },
  MARKETING: { agreed: false, agreedAt: null },
};

for (const type of reconsent) agreements[type] = { agreed: false, agreedAt: null };

/** 다시 동의를 받아야 하는 약관 (백엔드 GET /me/agreements/pending과 같은 모양). 마케팅은 예전에 동의했던 것으로 봅니다 */
let pendingTerms: TermsType[] = [...reconsent];

const agreementList = () =>
  (Object.keys(TERMS) as TermsType[]).map((type) => ({
    type,
    title: TERMS[type].title,
    required: TERMS[type].required,
    version: TERMS[type].version,
    ...agreements[type],
  }));

const notificationSettings = {
  pushEnabled: true,
  items: { like: true, comment: true, follow: true, message: true, planReminder: true, marketing: false },
  quietHours: { enabled: false, start: "23:00", end: "08:00" },
};

let devices = [
  { id: "d-1", name: "iPhone 15", platform: "IOS", appVersion: "1.0.0", lastActiveAt: ago(0), isCurrent: true, pushEnabled: true },
  { id: "d-2", name: "Galaxy Tab S9", platform: "ANDROID", appVersion: "1.0.0", lastActiveAt: ago(3), isCurrent: false, pushEnabled: true },
  { id: "d-3", name: "Chrome · macOS", platform: "WEB", appVersion: "web", lastActiveAt: ago(12), isCurrent: false, pushEnabled: false },
];

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

export const accountRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/terms/:type",
    handler: ({ params }) => {
      const type = params.type.toUpperCase() as TermsType;
      if (!TERMS[type]) throw new HttpError(404, "TERMS_NOT_FOUND", "약관을 찾을 수 없어요.");
      return { type, ...TERMS[type] };
    },
  },
  { method: "GET", path: "/api/v1/me/agreements", handler: agreementList },
  {
    method: "GET",
    path: "/api/v1/me/agreements/pending",
    handler: () =>
      pendingTerms.map((type) => ({ type, title: TERMS[type].title, required: TERMS[type].required, version: TERMS[type].version })),
  },
  {
    /** 재동의 화면: 동의를 한 번에 저장. 필수가 빠지면 400, 보내지 않은 선택 약관은 거절(동의 안 함)로 둡니다. (백엔드와 같은 오류 코드) */
    method: "POST",
    path: "/api/v1/me/agreements",
    handler: ({ body }) => {
      const agreed = new Set<TermsType>();
      for (const value of Array.isArray(body?.agreedTerms) ? body.agreedTerms : []) {
        const type = String(value).toUpperCase() as TermsType;
        if (!TERMS[type]) throw new HttpError(400, "UNKNOWN_TERMS", "알 수 없는 약관이에요.");
        agreed.add(type);
      }
      if (pendingTerms.some((type) => TERMS[type].required && !agreed.has(type))) {
        throw new HttpError(400, "REQUIRED_TERMS_NOT_AGREED", "필수 약관에 동의해 주세요.");
      }
      for (const type of agreed) {
        if (!agreements[type].agreed) agreements[type] = { agreed: true, agreedAt: new Date().toISOString() };
      }
      for (const type of pendingTerms) {
        if (!agreed.has(type)) agreements[type] = { agreed: false, agreedAt: null };
      }
      notificationSettings.items.marketing = agreements.MARKETING.agreed;
      pendingTerms = [];
      return agreementList();
    },
  },
  {
    /** 필수 약관은 철회할 수 없고, 선택 약관(마케팅)만 바꿀 수 있습니다. 이미 같은 상태면 그대로 둡니다. (백엔드와 같은 오류 코드) */
    method: "PATCH",
    path: "/api/v1/me/agreements",
    handler: ({ body }) => {
      if (typeof body?.agreed !== "boolean") throw new HttpError(400, "INVALID_INPUT", "입력값을 확인해 주세요.");
      const type = String(body?.type ?? "").toUpperCase() as TermsType;
      if (!TERMS[type]) throw new HttpError(400, "UNKNOWN_TERMS", "알 수 없는 약관이에요.");
      if (TERMS[type].required) {
        throw new HttpError(400, "REQUIRED_TERMS_CANNOT_BE_REVOKED", "필수 약관은 철회할 수 없어요. 더 이상 이용하지 않으려면 회원 탈퇴를 진행해 주세요.");
      }
      const agreed: boolean = body.agreed;
      if (agreements[type].agreed !== agreed) {
        agreements[type] = { agreed, agreedAt: agreed ? new Date().toISOString() : null };
      }
      notificationSettings.items.marketing = agreed;
      return agreementList();
    },
  },
  // 탈퇴 신청(POST /api/v1/me/withdrawal)은 세션을 끊어야 해서 auth.ts에 있습니다.

  // ── 푸시 알림 설정 ──────────────────────────────────
  { method: "GET", path: "/api/v1/me/notification-settings", handler: () => notificationSettings },
  {
    method: "PUT",
    path: "/api/v1/me/notification-settings",
    handler: ({ body }) => {
      if (typeof body?.pushEnabled === "boolean") notificationSettings.pushEnabled = body.pushEnabled;
      if (body?.items && typeof body.items === "object") {
        for (const key of Object.keys(notificationSettings.items) as (keyof typeof notificationSettings.items)[]) {
          if (typeof body.items[key] === "boolean") notificationSettings.items[key] = body.items[key];
        }
        agreements.MARKETING = { agreed: notificationSettings.items.marketing, agreedAt: notificationSettings.items.marketing ? new Date().toISOString() : null };
      }
      if (body?.quietHours) {
        const { enabled, start, end } = body.quietHours;
        if (!HHMM.test(start) || !HHMM.test(end)) throw new HttpError(400, "INVALID_INPUT", "방해 금지 시간을 확인해 주세요.");
        notificationSettings.quietHours = { enabled: !!enabled, start, end };
      }
      return notificationSettings;
    },
  },

  // ── 디바이스 (푸시 토큰) ────────────────────────────
  { method: "GET", path: "/api/v1/me/devices", handler: () => devices },
  {
    method: "PATCH",
    path: "/api/v1/me/devices/:id",
    handler: ({ params, body }) => {
      const d = devices.find((x) => x.id === params.id) ?? notFound("디바이스");
      if (typeof body?.pushEnabled === "boolean") d.pushEnabled = body.pushEnabled;
      return d;
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/me/devices/:id",
    handler: ({ params }) => {
      const d = devices.find((x) => x.id === params.id) ?? notFound("디바이스");
      if (d.isCurrent) throw new HttpError(400, "CURRENT_DEVICE", "지금 사용 중인 기기는 로그아웃으로 해제해 주세요.");
      devices = devices.filter((x) => x.id !== d.id);
      return undefined;
    },
  },
];
