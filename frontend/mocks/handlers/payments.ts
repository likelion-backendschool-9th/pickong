import { collectionDto, db, findUser, hasPurchased, ME_ID, nextId } from "../db.ts";
import type { PurchaseRow } from "../fixtures/types.ts";
import { HttpError, notFound, type Route } from "../router.ts";

/*
 * 카드 결제 · 구매 내역 · 환불 신청.
 * 실제 서비스는 PG 결제창(카드 정보 입력)을 띄우고, 승인 결과(paymentKey)를 서버가 PG 승인 API로 확인합니다.
 * mock은 결제창 단계를 건너뛰고 승인된 것으로 처리합니다. 카드 번호는 받지 않습니다.
 */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

/** 구매 후 7일 안에만 환불 신청할 수 있습니다. */
export const REFUND_DAYS = 7;
const CARD_COMPANIES = ["신한카드", "삼성카드", "현대카드", "KB국민카드", "롯데카드", "하나카드", "우리카드", "BC카드", "NH농협카드"];

/** 결제창을 열기 전에 만든 주문 (orderId → 주문) */
const orders = new Map<string, { collectionId: string; amount: number; orderName: string }>();

// 화면 확인용 구매 기록: 다른 회원이 산 지민님 컬렉션. 하나는 환불 신청 상태라 관리자 환불 화면에 보입니다.
db.purchases.push(
  { id: "pur-1", userId: "u-minsu", collectionId: "c-jimin-hidden", amount: 1900, method: "CARD", card: { company: "신한카드", installment: 0 }, purchasedAt: ago(4), status: "PAID", refund: null },
  { id: "pur-2", userId: "u-haeun", collectionId: "c-jimin-hidden", amount: 1900, method: "CARD", card: { company: "현대카드", installment: 0 }, purchasedAt: ago(2), status: "REFUND_REQUESTED", refund: { reason: "WRONG_PURCHASE", detail: "다른 컬렉션과 헷갈려서 잘못 샀어요.", requestedAt: ago(1), processedAt: null, rejectReason: null } },
);

const purchaseOr404 = (id: string) => db.purchases.find((p) => p.id === id && p.userId === ME_ID) ?? notFound("구매 내역");

const statusOf = (p: PurchaseRow) => p.status ?? "PAID";

export function refundDeadline(p: PurchaseRow) {
  return new Date(new Date(p.purchasedAt).getTime() + REFUND_DAYS * 86_400_000).toISOString();
}

export function purchaseDto(p: PurchaseRow) {
  const c = db.collections.find((x) => x.id === p.collectionId);
  const status = statusOf(p);
  return {
    id: p.id,
    collection: c
      ? { id: c.id, title: c.title, coverImageUrl: collectionDto(c).coverImageUrl, ownerNickname: findUser(c.ownerId)?.nickname ?? "", ownerUsername: findUser(c.ownerId)?.username ?? "" }
      : null,
    buyer: (() => {
      const u = findUser(p.userId);
      return u ? { username: u.username, nickname: u.nickname } : null;
    })(),
    amount: p.amount,
    method: p.method,
    card: p.card ?? null,
    purchasedAt: p.purchasedAt,
    status,
    refundDeadline: refundDeadline(p),
    refundable: status === "PAID" && Date.now() < new Date(refundDeadline(p)).getTime(),
    refund: p.refund ?? null,
  };
}

export const paymentRoutes: Route[] = [
  {
    /** 결제 준비: 서버가 가격을 정해 주문을 만듭니다. 화면이 보낸 금액은 믿지 않습니다. */
    method: "POST",
    path: "/api/v1/payments/orders",
    handler: ({ body }) => {
      const c = db.collections.find((x) => x.id === body?.collectionId) ?? notFound("컬렉션");
      if (!c.price) throw new HttpError(400, "NOT_FOR_SALE", "판매 중인 컬렉션이 아니에요.");
      if (c.ownerId === ME_ID) throw new HttpError(400, "OWN_COLLECTION", "내 컬렉션은 구매할 수 없어요.");
      if (hasPurchased(c.id)) throw new HttpError(409, "ALREADY_PURCHASED", "이미 구매한 컬렉션이에요.");
      const orderId = `order-${Date.now()}`;
      const order = { collectionId: c.id, amount: c.price, orderName: c.title };
      orders.set(orderId, order);
      return { orderId, ...order, sellerUsername: findUser(c.ownerId)?.username ?? "", cardCompanies: CARD_COMPANIES };
    },
  },
  {
    /** 결제 승인 (mock PG). 금액이 주문과 다르면 거절합니다. */
    method: "POST",
    path: "/api/v1/payments/confirm",
    delay: 1200,
    handler: ({ body }) => {
      const order = orders.get(String(body?.orderId ?? ""));
      if (!order) throw new HttpError(404, "NOT_FOUND", "주문이 만료됐어요. 다시 시도해 주세요.");
      if (Number(body?.amount) !== order.amount) throw new HttpError(409, "AMOUNT_MISMATCH", "결제 금액이 주문과 달라요.");
      const company = String(body?.cardCompany ?? "");
      if (!CARD_COMPANIES.includes(company)) throw new HttpError(400, "INVALID_INPUT", "카드사를 골라 주세요.");
      const installment = Number(body?.installment ?? 0);
      if (![0, 2, 3, 6].includes(installment)) throw new HttpError(400, "INVALID_INPUT", "할부 개월을 확인해 주세요.");
      if (installment > 0 && order.amount < 50000) throw new HttpError(400, "INVALID_INPUT", "5만 원 이상만 할부할 수 있어요.");
      if (hasPurchased(order.collectionId)) throw new HttpError(409, "ALREADY_PURCHASED", "이미 구매한 컬렉션이에요.");
      const purchase: PurchaseRow = {
        id: nextId("pur"),
        userId: ME_ID,
        collectionId: order.collectionId,
        amount: order.amount,
        method: "CARD",
        card: { company, installment },
        purchasedAt: new Date().toISOString(),
        status: "PAID",
        refund: null,
      };
      db.purchases.push(purchase);
      orders.delete(String(body.orderId));
      return purchaseDto(purchase);
    },
  },
  {
    method: "GET",
    path: "/api/v1/me/purchases",
    handler: () =>
      db.purchases
        .filter((p) => p.userId === ME_ID)
        .sort((a, b) => b.purchasedAt.localeCompare(a.purchasedAt))
        .map(purchaseDto),
  },
  { method: "GET", path: "/api/v1/me/purchases/:id", handler: ({ params }) => purchaseDto(purchaseOr404(params.id)) },
  {
    method: "POST",
    path: "/api/v1/purchases/:id/refunds",
    delay: 500,
    handler: ({ params, body }) => {
      const p = purchaseOr404(params.id);
      const dto = purchaseDto(p);
      if (dto.status !== "PAID") throw new HttpError(409, "ALREADY_REQUESTED", "이미 환불을 신청한 구매예요.");
      if (!dto.refundable) throw new HttpError(400, "REFUND_EXPIRED", `구매 후 ${REFUND_DAYS}일이 지나 환불할 수 없어요.`);
      const reason = String(body?.reason ?? "");
      if (!reason) throw new HttpError(400, "INVALID_INPUT", "환불 사유를 골라 주세요.");
      p.status = "REFUND_REQUESTED";
      p.refund = { reason, detail: String(body?.detail ?? "").slice(0, 300), requestedAt: new Date().toISOString(), processedAt: null, rejectReason: null };
      return purchaseDto(p);
    },
  },
];
