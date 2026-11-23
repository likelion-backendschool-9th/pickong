import { HttpError, notFound, type Route } from "../router.ts";

/* 계정 관리 · 약관 · 알림 설정 · 디바이스 (mock 메모리 상태) */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

type TermsType = "SERVICE" | "PRIVACY" | "MARKETING";

const TERMS: Record<TermsType, { title: string; required: boolean; version: string; effectiveDate: string; sections: { heading: string; body: string }[] }> = {
  SERVICE: {
    title: "서비스 이용약관",
    required: true,
    version: "1.0",
    effectiveDate: "2026-11-01",
    sections: [
      { heading: "제1조 (목적)", body: "이 약관은 찜콩(이하 ‘서비스’)이 제공하는 장소 저장 · 공유 · 일정 기능의 이용 조건과 절차, 회원과 서비스의 권리 · 의무를 정합니다." },
      { heading: "제2조 (회원 가입)", body: "회원 가입은 이용자가 약관에 동의하고 가입 신청을 한 뒤, 서비스가 이를 승낙하면 완료됩니다." },
      { heading: "제3조 (게시물)", body: "회원이 올린 사진 · 후기 · 컬렉션의 권리는 회원에게 있습니다. 다른 사람의 권리를 침해하는 게시물은 신고에 따라 숨김 처리될 수 있습니다." },
      { heading: "제4조 (유료 콘텐츠)", body: "유료 컬렉션은 구매 후 7일 이내, 장소 목록을 열람하지 않은 경우 환불을 신청할 수 있습니다." },
      { heading: "제5조 (탈퇴)", body: "회원은 언제든지 탈퇴할 수 있으며, 탈퇴 후 30일 동안은 같은 소셜 계정으로 다시 가입할 수 없습니다." },
    ],
  },
  PRIVACY: {
    title: "개인정보 수집 및 이용",
    required: true,
    version: "1.0",
    effectiveDate: "2026-11-01",
    sections: [
      { heading: "수집 항목", body: "필수: 소셜 계정 식별자(카카오 · Google 회원번호), 닉네임, 사용자 ID\n선택: 이메일 · 프로필 사진(소셜 계정에서 제공하는 경우), 소개, 활동 지역" },
      { heading: "이용 목적", body: "회원 식별, 본인 확인, 서비스 제공, 부정 이용 방지" },
      { heading: "보관 기간", body: "탈퇴 시 바로 파기합니다. 단, 관련 법령에 따라 보관해야 하는 정보(결제 기록 5년 등)는 해당 기간 동안 보관합니다." },
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

const agreements: Record<TermsType, { agreed: boolean; agreedAt: string | null }> = {
  SERVICE: { agreed: true, agreedAt: ago(40) },
  PRIVACY: { agreed: true, agreedAt: ago(40) },
  MARKETING: { agreed: false, agreedAt: null },
};

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
    path: "/api/terms/:type",
    handler: ({ params }) => {
      const type = params.type.toUpperCase() as TermsType;
      const terms = TERMS[type] ?? notFound("약관");
      return { type, ...terms };
    },
  },
  { method: "GET", path: "/api/me/agreements", handler: agreementList },
  {
    /** 필수 약관은 철회할 수 없고, 선택 약관(마케팅)만 바꿀 수 있습니다. */
    method: "PATCH",
    path: "/api/me/agreements",
    handler: ({ body }) => {
      const type = String(body?.type ?? "") as TermsType;
      if (!TERMS[type]) throw new HttpError(400, "INVALID_INPUT", "약관 종류를 확인해 주세요.");
      if (TERMS[type].required) throw new HttpError(400, "REQUIRED_TERMS", "필수 약관은 철회할 수 없어요. 탈퇴로만 해지할 수 있어요.");
      const agreed = !!body?.agreed;
      agreements[type] = { agreed, agreedAt: agreed ? new Date().toISOString() : null };
      notificationSettings.items.marketing = agreed;
      return agreementList();
    },
  },
  {
    /** 탈퇴: 사유 확인. 소셜 로그인 전용이라 비밀번호는 받지 않습니다. */
    method: "POST",
    path: "/api/me/withdrawal",
    delay: 600,
    handler: ({ body }) => {
      if (!String(body?.reason ?? "")) throw new HttpError(400, "INVALID_INPUT", "탈퇴 사유를 선택해 주세요.");
      return undefined;
    },
  },

  // ── 푸시 알림 설정 ──────────────────────────────────
  { method: "GET", path: "/api/me/notification-settings", handler: () => notificationSettings },
  {
    method: "PUT",
    path: "/api/me/notification-settings",
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
  { method: "GET", path: "/api/me/devices", handler: () => devices },
  {
    method: "PATCH",
    path: "/api/me/devices/:id",
    handler: ({ params, body }) => {
      const d = devices.find((x) => x.id === params.id) ?? notFound("디바이스");
      if (typeof body?.pushEnabled === "boolean") d.pushEnabled = body.pushEnabled;
      return d;
    },
  },
  {
    method: "DELETE",
    path: "/api/me/devices/:id",
    handler: ({ params }) => {
      const d = devices.find((x) => x.id === params.id) ?? notFound("디바이스");
      if (d.isCurrent) throw new HttpError(400, "CURRENT_DEVICE", "지금 사용 중인 기기는 로그아웃으로 해제해 주세요.");
      devices = devices.filter((x) => x.id !== d.id);
      return undefined;
    },
  },
];
