import { db, findUser, ME_ID } from "../db.ts";
import { HttpError, type Route } from "../router.ts";

/* 크리에이터 신청 · 심사 · 정산 계좌 · 수익 (관리자 화면과 상태를 함께 씁니다) */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

export type ApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface CreatorApplication {
  id: string;
  userId: string;
  category: string;
  channelUrl: string;
  intro: string;
  plan: string;
  status: ApplicationStatus;
  submittedAt: string;
  reviewedAt: string | null;
  rejectReason: string | null;
}

export const creatorState = {
  applications: [
    { id: "ca-1", userId: "u-minsu", category: "FOOD", channelUrl: "https://blog.example.com/minsu", intro: "망원 · 합정 맛집을 3년째 기록하고 있어요.", plan: "동네별 점심 코스 컬렉션을 만들고 싶어요.", status: "PENDING", submittedAt: ago(1), reviewedAt: null, rejectReason: null },
    { id: "ca-2", userId: "u-haeun", category: "TRAVEL", channelUrl: "https://instagram.com/haeun.trip", intro: "주말마다 근교 당일치기를 다녀요.", plan: "경기 근교 당일치기 코스", status: "PENDING", submittedAt: ago(2), reviewedAt: null, rejectReason: null },
    { id: "ca-3", userId: "u-jimin", category: "CAFE", channelUrl: "https://instagram.com/jimin.cafe", intro: "성수 카페 전문", plan: "성수 숨은 카페 지도", status: "APPROVED", submittedAt: ago(60), reviewedAt: ago(58), rejectReason: null },
  ] as CreatorApplication[],
  /** 정산 계좌 (userId → 계좌). 계좌번호는 응답에서 가려서 보냅니다. */
  accounts: {
    "u-jimin": { bank: "국민은행", accountNumber: "123456789012", holder: "김지민" },
  } as Record<string, { bank: string; accountNumber: string; holder: string }>,
  /** 정산: 매월 말일 기준으로 다음 달 10일 지급 */
  settlements: [
    { id: "st-1", userId: "u-jimin", period: "2026-09", salesAmount: 38000, fee: 7600, amount: 30400, status: "PAID", paidAt: "2026-10-10T10:00:00+09:00" },
    { id: "st-2", userId: "u-jimin", period: "2026-10", salesAmount: 57000, fee: 11400, amount: 45600, status: "SCHEDULED", paidAt: null },
    { id: "st-3", userId: "u-walker", period: "2026-10", salesAmount: 9500, fee: 1900, amount: 7600, status: "SCHEDULED", paidAt: null },
  ] as { id: string; userId: string; period: string; salesAmount: number; fee: number; amount: number; status: "SCHEDULED" | "PAID" | "HELD"; paidAt: string | null }[],
};

/** 플랫폼 수수료 20% */
export const FEE_RATE = 0.2;

export const maskAccount = (n: string) => `${n.slice(0, 3)}${"*".repeat(Math.max(0, n.length - 6))}${n.slice(-3)}`;

export function accountDto(userId: string) {
  const a = creatorState.accounts[userId];
  return a ? { bank: a.bank, accountNumberMasked: maskAccount(a.accountNumber), holder: a.holder } : null;
}

const latestApplication = (userId: string) =>
  creatorState.applications.filter((a) => a.userId === userId).sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))[0] ?? null;

/** 응답에서 내부 userId를 뺍니다. */
function withoutUserId<T extends { userId: string }>(row: T): Omit<T, "userId"> {
  const copy: Partial<T> = { ...row };
  delete copy.userId;
  return copy as Omit<T, "userId">;
}

const applicationDto = (a: CreatorApplication | null) => (a ? withoutUserId(a) : null);

/** 승인 · 반려 처리 (관리자 화면에서 호출) */
export function reviewApplication(id: string, approve: boolean, rejectReason: string) {
  const a = creatorState.applications.find((x) => x.id === id);
  if (!a) throw new HttpError(404, "NOT_FOUND", "신청을 찾을 수 없어요.");
  if (a.status !== "PENDING") throw new HttpError(409, "ALREADY_REVIEWED", "이미 심사한 신청이에요.");
  if (!approve && !rejectReason.trim()) throw new HttpError(400, "INVALID_INPUT", "반려 사유를 입력해 주세요.");
  a.status = approve ? "APPROVED" : "REJECTED";
  a.reviewedAt = new Date().toISOString();
  a.rejectReason = approve ? null : rejectReason.trim();
  const user = findUser(a.userId);
  if (user && approve) user.isCreator = true;
  return a;
}

const CATEGORIES = ["CAFE", "FOOD", "TRAVEL", "CULTURE", "ETC"];

function earnings(userId: string) {
  const myCollections = new Set(db.collections.filter((c) => c.ownerId === userId).map((c) => c.id));
  const sales = db.purchases
    .filter((p) => myCollections.has(p.collectionId))
    .map((p) => {
      const refunded = p.status === "REFUNDED";
      const fee = refunded ? 0 : Math.round(p.amount * FEE_RATE);
      return {
        id: p.id,
        collectionTitle: db.collections.find((c) => c.id === p.collectionId)?.title ?? "삭제된 컬렉션",
        buyerNickname: findUser(p.userId)?.nickname ?? "알 수 없음",
        amount: p.amount,
        fee,
        net: refunded ? 0 : p.amount - fee,
        status: refunded ? "REFUNDED" : "PAID",
        soldAt: p.purchasedAt,
      };
    })
    .sort((a, b) => b.soldAt.localeCompare(a.soldAt));
  const settlements = creatorState.settlements.filter((s) => s.userId === userId).sort((a, b) => b.period.localeCompare(a.period));
  const paid = sales.filter((s) => s.status === "PAID");
  return {
    summary: {
      totalSales: paid.reduce((n, s) => n + s.amount, 0) + settlements.reduce((n, s) => n + s.salesAmount, 0),
      unsettled: paid.reduce((n, s) => n + s.net, 0),
      scheduled: settlements.filter((s) => s.status === "SCHEDULED").reduce((n, s) => n + s.amount, 0),
      paidTotal: settlements.filter((s) => s.status === "PAID").reduce((n, s) => n + s.amount, 0),
      feeRate: FEE_RATE,
    },
    sales,
    settlements: settlements.map(withoutUserId),
  };
}

export const creatorRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/me/creator",
    handler: () => {
      const me = findUser(ME_ID)!;
      const app = latestApplication(ME_ID);
      return {
        status: me.isCreator ? "APPROVED" : (app?.status ?? "NONE"),
        application: applicationDto(app),
        history: creatorState.applications
          .filter((a) => a.userId === ME_ID)
          .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
          .map(applicationDto),
        account: accountDto(ME_ID),
      };
    },
  },
  {
    method: "POST",
    path: "/api/v1/me/creator/applications",
    delay: 500,
    handler: ({ body }) => {
      const me = findUser(ME_ID)!;
      if (me.isCreator) throw new HttpError(409, "ALREADY_CREATOR", "이미 크리에이터예요.");
      if (latestApplication(ME_ID)?.status === "PENDING") throw new HttpError(409, "ALREADY_PENDING", "심사 중인 신청이 있어요.");
      const category = String(body?.category ?? "");
      const intro = String(body?.intro ?? "").trim();
      const plan = String(body?.plan ?? "").trim();
      const channelUrl = String(body?.channelUrl ?? "").trim();
      if (!CATEGORIES.includes(category)) throw new HttpError(400, "INVALID_INPUT", "활동 분야를 골라 주세요.");
      if (intro.length < 10) throw new HttpError(400, "INVALID_INPUT", "소개를 10자 이상 써 주세요.");
      if (!plan) throw new HttpError(400, "INVALID_INPUT", "만들고 싶은 컬렉션을 적어 주세요.");
      if (channelUrl && !/^https?:\/\/\S+\.\S+/.test(channelUrl)) throw new HttpError(400, "INVALID_INPUT", "채널 주소를 확인해 주세요.");
      const app: CreatorApplication = {
        id: `ca-${Date.now()}`,
        userId: ME_ID,
        category,
        channelUrl,
        intro,
        plan,
        status: "PENDING",
        submittedAt: new Date().toISOString(),
        reviewedAt: null,
        rejectReason: null,
      };
      creatorState.applications.push(app);
      return applicationDto(app);
    },
  },
  {
    /** 정산 계좌 등록 · 변경. mock은 예금주 확인 없이 저장합니다. */
    method: "PUT",
    path: "/api/v1/me/creator/account",
    delay: 500,
    handler: ({ body }) => {
      if (!findUser(ME_ID)!.isCreator) throw new HttpError(403, "NOT_CREATOR", "크리에이터 승인 후 등록할 수 있어요.");
      const bank = String(body?.bank ?? "").trim();
      const accountNumber = String(body?.accountNumber ?? "").replace(/\D/g, "");
      const holder = String(body?.holder ?? "").trim();
      if (!bank) throw new HttpError(400, "INVALID_INPUT", "은행을 골라 주세요.");
      if (accountNumber.length < 10 || accountNumber.length > 14) throw new HttpError(400, "INVALID_INPUT", "계좌번호는 숫자 10~14자리로 입력해 주세요.");
      if (!holder) throw new HttpError(400, "INVALID_INPUT", "예금주를 입력해 주세요.");
      creatorState.accounts[ME_ID] = { bank, accountNumber, holder };
      return accountDto(ME_ID);
    },
  },
  {
    method: "GET",
    path: "/api/v1/me/creator/earnings",
    handler: () => {
      if (!findUser(ME_ID)!.isCreator) throw new HttpError(403, "NOT_CREATOR", "크리에이터 승인 후 볼 수 있어요.");
      return earnings(ME_ID);
    },
  },
];
