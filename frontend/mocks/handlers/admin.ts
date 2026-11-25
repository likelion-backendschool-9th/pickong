import { buildReportTarget, resolveMockVisibility, type ReportSnapshotSource, type ReportVisibility } from "../../src/app/(admin)/admin/reports/_lib/reportTargetPreview.ts";
import { excerpt } from "../../src/app/(admin)/admin/reports/_lib/reportContentComparison.ts";
import { normalizeReason, reasonError } from "../../src/app/(admin)/admin/_lib/reason.ts";
import { collectionDto, db, findUser, postDto } from "../db.ts";
import { HttpError, RawResponse, notFound, type Route } from "../router.ts";
import { accountDto, creatorState, reviewApplication } from "./creator.ts";
import { purchaseDto } from "./payments.ts";

/*
 * 관리자 화면 mock. 회원과 별개인 관리자 계정(admin)으로 들어옵니다.
 * mock은 권한 확인을 하지 않습니다. 실제 백엔드는 /api/v1/admin/**에서 관리자 토큰만 받습니다.
 */

const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();

type TargetType = "POST" | "COMMENT" | "USER" | "COLLECTION";
type ReportAction = "DISMISS" | "HIDE_CONTENT" | "SANCTION_USER";
type SanctionType = "WARNING" | "SUSPEND_7" | "SUSPEND_30" | "BAN";

interface ReportRow {
  id: string;
  targetType: TargetType;
  targetId: string;
  reportedUserId: string;
  reporterId: string;
  reason: string;
  detail: string;
  createdAt: string;
  status: "PENDING" | "RESOLVED";
  resolution: { action: ReportAction; memo: string; resolvedAt: string } | null;
  /** textScope는 비교에만 쓰고 응답에는 넣지 않습니다. currentBody가 있으면 대상 본문 대신 씁니다. */
  snapshot?: ReportSnapshotSource | null;
  currentBody?: string | null;
  visibility?: ReportVisibility;
}

const reports: ReportRow[] = [
  {
    id: "rp-1",
    targetType: "POST",
    targetId: "post-5",
    reportedUserId: "u-walker",
    reporterId: "u-haeun",
    reason: "SPAM",
    detail: "같은 사진을 계속 올려요.",
    createdAt: ago(0.2),
    status: "PENDING",
    resolution: null,
    snapshot: { text: "광고 문구가 포함된 게시물이었어요.\n구매 링크가 반복됐어요.", imageUrl: "/images/photos/park.jpg" },
  },
  {
    id: "rp-2",
    targetType: "COMMENT",
    targetId: "cm-2",
    reportedUserId: "u-minsu",
    reporterId: "u-yujin",
    reason: "ABUSE",
    detail: "",
    createdAt: ago(0.6),
    status: "PENDING",
    resolution: null,
    visibility: "HIDDEN",
  },
  { id: "rp-3", targetType: "USER", targetId: "u-walker", reportedUserId: "u-walker", reporterId: "u-jimin", reason: "IMPERSONATION", detail: "다른 크리에이터를 사칭하는 것 같아요.", createdAt: ago(1), status: "PENDING", resolution: null },
  { id: "rp-4", targetType: "COLLECTION", targetId: "c-jimin-photo", reportedUserId: "u-jimin", reporterId: "u-minsu", reason: "COPYRIGHT", detail: "제 블로그 사진을 그대로 썼어요.", createdAt: ago(3), status: "RESOLVED", resolution: { action: "DISMISS", memo: "직접 촬영한 사진으로 확인", resolvedAt: ago(2) } },
  {
    id: "rp-5",
    targetType: "POST",
    targetId: "post-report-full",
    reportedUserId: "u-walker",
    reporterId: "u-haeun",
    reason: "ABUSE",
    detail: "첫 줄은 같고 뒷줄만 바뀌었어요.",
    createdAt: ago(0.15),
    status: "PENDING",
    resolution: null,
    snapshot: { text: "안녕하세요.\n부적절한 내용입니다.", imageUrl: null, textScope: "FULL" },
    currentBody: "안녕하세요.\n수정된 내용입니다.",
  },
  {
    id: "rp-6",
    targetType: "COMMENT",
    targetId: "comment-report-full",
    reportedUserId: "u-minsu",
    reporterId: "u-yujin",
    reason: "ABUSE",
    detail: "",
    createdAt: ago(0.35),
    status: "PENDING",
    resolution: null,
    snapshot: { text: "첫 줄입니다.\n가운데입니다.\n신고 당시 마지막", imageUrl: null, textScope: "FULL" },
    currentBody: "첫 줄입니다.\n가운데입니다.\n수정된 마지막",
  },
  {
    id: "rp-7",
    targetType: "POST",
    targetId: "post-report-legacy",
    reportedUserId: "u-walker",
    reporterId: "u-jimin",
    reason: "ETC",
    detail: "",
    createdAt: ago(0.8),
    status: "PENDING",
    resolution: null,
    snapshot: { text: "같은 첫 줄\n신고 당시 둘째", imageUrl: null },
    currentBody: "같은 첫 줄\n현재 둘째",
  },
  {
    id: "rp-8",
    targetType: "POST",
    targetId: "post-report-same",
    reportedUserId: "u-seoyeon",
    reporterId: "u-haeun",
    reason: "SPAM",
    detail: "",
    createdAt: ago(1.4),
    status: "PENDING",
    resolution: null,
    snapshot: { text: "동일한 본문\n둘째 줄", imageUrl: null, textScope: "FULL" },
    currentBody: "동일한 본문\n둘째 줄",
  },
  {
    id: "rp-9",
    targetType: "POST",
    targetId: "post-report-deleted",
    reportedUserId: "u-walker",
    reporterId: "u-minsu",
    reason: "SPAM",
    detail: "",
    createdAt: ago(1.8),
    status: "PENDING",
    resolution: null,
    visibility: "DELETED",
    snapshot: { text: "삭제 전 본문", imageUrl: null, textScope: "FULL" },
    currentBody: "지운 뒤의 본문",
  },
  {
    id: "rp-10",
    targetType: "COMMENT",
    targetId: "comment-report-hidden",
    reportedUserId: "u-jimin",
    reporterId: "u-haeun",
    reason: "ABUSE",
    detail: "",
    createdAt: ago(2.2),
    status: "PENDING",
    resolution: null,
    visibility: "HIDDEN",
    snapshot: { text: "숨김 첫 줄\n신고 당시", imageUrl: null, textScope: "FULL" },
    currentBody: "숨김 첫 줄\n현재 본문",
  },
];

/** 숨김 처리한 콘텐츠 ("POST:post-5" 형태). mock은 표시만 하고 피드에서 빼지는 않습니다. */
const hidden = new Set<string>();

interface MockSanction { id: string; type: SanctionType; reason: string; startedAt: string; until: string | null; endedAt: string | null }
let nextSanctionId = 1;
const sanctions: Record<string, MockSanction[]> = {
  "u-walker": [{ id: "1", type: "WARNING", reason: "스팸성 게시물 반복", startedAt: ago(10), until: null, endedAt: null }],
};

const SANCTION_DAYS: Record<SanctionType, number | null> = { WARNING: null, SUSPEND_7: 7, SUSPEND_30: 30, BAN: null };

const userSummary = (id: string) => {
  const u = findUser(id);
  return u ? { id: u.id, username: u.username, nickname: u.nickname, profileImageUrl: u.profileImageUrl } : null;
};

function targetPreview(r: ReportRow) {
  const source = visibleTarget(r);
  const hasCurrent = Object.hasOwn(r, "currentBody");
  const currentVisibility = resolveMockVisibility(r.visibility, source.visibility, hasCurrent);
  const visibility = currentVisibility === "LIVE" && hidden.has(`${r.targetType}:${r.targetId}`) ? "HIDDEN" : currentVisibility;
  const body = hasCurrent ? (r.currentBody ?? null) : source.body;
  return buildReportTarget({
    targetType: r.targetType,
    visibility,
    snapshot: r.snapshot ?? null,
    currentBody: body,
    link: source.link,
    liveFallback: excerpt(body) ?? source.blankTitle,
    deletedText: source.deletedText,
    unavailableText: source.unavailableText,
  });
}

function visibleTarget(r: ReportRow): {
  body: string | null;
  link: string | null;
  visibility: ReportVisibility;
  blankTitle: string;
  deletedText: string;
  unavailableText: string;
} {
  if (r.targetType === "POST") {
    const p = db.posts.find((x) => x.id === r.targetId);
    return {
      body: p ? p.content : null,
      link: p ? `/posts/${r.targetId}` : null,
      visibility: p ? "LIVE" : "UNAVAILABLE",
      blankTitle: "게시물",
      deletedText: "삭제된 게시물",
      unavailableText: "확인할 수 없는 게시물",
    };
  }
  if (r.targetType === "COMMENT") {
    const c = db.comments.find((x) => x.id === r.targetId);
    return {
      body: c ? c.content : null,
      link: c ? `/posts/${c.postId}` : null,
      visibility: c ? "LIVE" : "UNAVAILABLE",
      blankTitle: "댓글",
      deletedText: "삭제된 댓글",
      unavailableText: "확인할 수 없는 댓글",
    };
  }
  if (r.targetType === "COLLECTION") {
    const c = db.collections.find((x) => x.id === r.targetId);
    return {
      body: c ? c.title : null,
      link: c ? `/collections/${r.targetId}` : null,
      visibility: c ? "LIVE" : "UNAVAILABLE",
      blankTitle: "컬렉션",
      deletedText: "확인할 수 없는 컬렉션",
      unavailableText: "확인할 수 없는 컬렉션",
    };
  }
  const u = findUser(r.targetId);
  const label = u ? `${u.nickname} (@${u.username})` : null;
  return {
    body: label,
    link: u ? `/users/${u.username}` : null,
    visibility: u ? "LIVE" : "UNAVAILABLE",
    blankTitle: label ?? "사용자",
    deletedText: "탈퇴한 사용자",
    unavailableText: "확인할 수 없는 사용자",
  };
}

const reportDto = (r: ReportRow) => ({
  id: r.id,
  targetType: r.targetType,
  targetId: r.targetId,
  target: targetPreview(r),
  reportedUser: userSummary(r.reportedUserId),
  reporter: userSummary(r.reporterId),
  reason: r.reason,
  detail: r.detail,
  createdAt: r.createdAt,
  status: r.status,
  resolution: r.resolution,
});

function activeSanctions(userId: string) {
  return (sanctions[userId] ?? []).filter((s) => !s.endedAt && (!s.until || new Date(s.until).getTime() > Date.now()))
    .sort((a, b) => Number(a.type === "WARNING") - Number(b.type === "WARNING") || b.startedAt.localeCompare(a.startedAt) || Number(b.id) - Number(a.id));
}

const sanctionOf = (userId: string) => activeSanctions(userId)[0] ?? null;
const sanctionDto = (s: MockSanction) => ({ id: s.id, type: s.type, reason: s.reason, startedAt: s.startedAt, until: s.until });

const adminUserDto = (id: string) => {
  const u = findUser(id)!;
  const s = sanctionOf(id);
  return {
    ...userSummary(id)!,
    isCreator: !!u.isCreator,
    reportCount: reports.filter((r) => r.reportedUserId === id).length,
    postCount: db.posts.filter((p) => p.authorId === id).length,
    status: !s || s.type === "WARNING" ? "ACTIVE" : s.type === "BAN" ? "BANNED" : "SUSPENDED",
    sanction: s ? sanctionDto(s) : null,
    sanctions: activeSanctions(id).map(sanctionDto),
  };
};

function sanction(userId: string, type: SanctionType, reason: string) {
  if (!findUser(userId)) notFound("사용자");
  if (!Object.hasOwn(SANCTION_DAYS, type)) throw new HttpError(400, "INVALID_INPUT", "제재 종류를 확인해 주세요.");
  const validation = reasonError(reason, true);
  if (validation) throw new HttpError(400, "INVALID_INPUT", validation);
  const restriction = activeSanctions(userId).find((s) => s.type !== "WARNING");
  if (type !== "WARNING" && restriction) {
    if (type !== "BAN" || restriction.type === "BAN") throw new HttpError(409, "ACTIVE_SANCTION_EXISTS", "유효한 이용 제한 제재가 있어요.");
    restriction.endedAt = new Date().toISOString();
  }
  const days = SANCTION_DAYS[type];
  const saved: MockSanction = { id: String(++nextSanctionId), type, reason: normalizeReason(reason), startedAt: new Date().toISOString(), until: days ? ago(-days) : null, endedAt: null };
  (sanctions[userId] ??= []).push(saved);
  return sanctionDto(saved);
}

function contentList(type: string, q: string) {
  const query = q.trim().toLowerCase();
  if (type === "COLLECTION") {
    return db.collections
      .map((c) => {
        const dto = collectionDto(c);
        return {
          id: c.id,
          type: "COLLECTION" as const,
          title: c.title,
          author: userSummary(c.ownerId),
          imageUrl: dto.coverImageUrl,
          visibility: c.visibility,
          price: c.price ?? null,
          hidden: hidden.has(`COLLECTION:${c.id}`),
          reportCount: reports.filter((r) => r.targetType === "COLLECTION" && r.targetId === c.id).length,
          createdAt: c.createdAt,
          link: `/collections/${c.id}`,
        };
      })
      .filter((c) => !query || (c.title + (c.author?.nickname ?? "")).toLowerCase().includes(query));
  }
  return db.posts
    .map((p) => {
      const dto = postDto(p);
      return {
        id: p.id,
        type: "POST" as const,
        title: p.content.split("\n")[0],
        author: userSummary(p.authorId),
        imageUrl: dto.photos[0]?.url ?? null,
        visibility: p.visibility,
        price: null,
        hidden: hidden.has(`POST:${p.id}`),
        reportCount: reports.filter((r) => r.targetType === "POST" && r.targetId === p.id).length,
        createdAt: p.createdAt,
        link: `/posts/${p.id}`,
      };
    })
    .filter((c) => !query || (c.title + (c.author?.nickname ?? "")).toLowerCase().includes(query))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export const adminRoutes: Route[] = [
  {
    method: "GET",
    path: "/api/v1/admin/summary",
    handler: () => ({
      pendingReports: reports.filter((r) => r.status === "PENDING").length,
      pendingCreatorApplications: creatorState.applications.filter((a) => a.status === "PENDING").length,
      pendingRefunds: db.purchases.filter((p) => p.status === "REFUND_REQUESTED").length,
      scheduledSettlements: creatorState.settlements.filter((s) => s.status === "SCHEDULED").length,
      sanctionedUsers: db.users.filter((u) => sanctionOf(u.id)).length,
      hiddenContents: hidden.size,
    }),
  },

  // ── 신고 ──────────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/reports",
    handler: ({ query }) => {
      const status = query.get("status") ?? "PENDING";
      return reports.filter((r) => r.status === status).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map(reportDto);
    },
  },
  {
    /** 신고 처리: 기각 / 콘텐츠 숨김 / 작성자 제재 */
    method: "POST",
    path: "/api/v1/admin/reports/:id/resolve",
    handler: ({ params, body }) => {
      const r = reports.find((x) => x.id === params.id) ?? notFound("신고");
      if (r.status === "RESOLVED") throw new HttpError(409, "ALREADY_RESOLVED", "이미 처리한 신고예요.");
      const action = String(body?.action ?? "") as ReportAction;
      const memo = normalizeReason(String(body?.memo ?? ""));
      const validation = reasonError(memo, action !== "DISMISS");
      if (validation) throw new HttpError(400, "INVALID_INPUT", validation);
      if (r.targetType === "COLLECTION" && action !== "DISMISS") throw new HttpError(501, "NOT_IMPLEMENTED", "컬렉션 관리는 준비 중이에요.");
      if (action === "HIDE_CONTENT") {
        if (r.targetType === "USER") throw new HttpError(400, "INVALID_INPUT", "사용자 신고는 제재로 처리해 주세요.");
        if (["DELETED", "UNAVAILABLE"].includes(targetPreview(r).visibility)) notFound("콘텐츠");
        hidden.add(`${r.targetType}:${r.targetId}`);
      } else if (action === "SANCTION_USER") {
        sanction(r.reportedUserId, String(body?.sanctionType ?? "") as SanctionType, memo);
      } else if (action !== "DISMISS") {
        throw new HttpError(400, "INVALID_INPUT", "처리 방법을 골라 주세요.");
      }
      r.status = "RESOLVED";
      r.resolution = { action, memo, resolvedAt: new Date().toISOString() };
      return reportDto(r);
    },
  },

  // ── 사용자 제재 ────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/users",
    handler: ({ query }) => {
      const q = (query.get("q") ?? "").trim().toLowerCase();
      const filter = query.get("filter") ?? "ALL";
      return db.users
        .map((u) => adminUserDto(u.id))
        .filter((u) => !q || (u.nickname + u.username).toLowerCase().includes(q))
        .filter((u) => filter === "ALL" || (filter === "SANCTIONED" ? !!u.sanction : u.reportCount > 0))
        .sort((a, b) => b.reportCount - a.reportCount);
    },
  },
  {
    method: "POST",
    path: "/api/v1/admin/users/:id/sanctions",
    handler: ({ params, body }) => {
      const result = sanction(params.id, String(body?.type ?? "") as SanctionType, String(body?.reason ?? ""));
      return new RawResponse(201, { "Content-Type": "application/json" }, JSON.stringify(result));
    },
  },
  {
    method: "DELETE",
    path: "/api/v1/admin/users/:id/sanctions/:sanctionId",
    handler: ({ params, body }) => {
      if (!findUser(params.id)) notFound("사용자");
      const selected = (sanctions[params.id] ?? []).find((s) => s.id === params.sanctionId) ?? notFound("제재");
      if (!activeSanctions(params.id).some((s) => s.id === selected.id)) throw new HttpError(409, "SANCTION_ALREADY_ENDED", "이미 종료된 제재예요.");
      const validation = reasonError(String(body?.reason ?? ""), false);
      if (validation) throw new HttpError(400, "INVALID_INPUT", validation);
      selected.endedAt = new Date().toISOString();
      return undefined;
    },
  },

  // ── 콘텐츠 관리 ────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/feeds",
    handler: ({ query }) => contentList("POST", query.get("q") ?? ""),
  },
  {
    method: "PATCH",
    path: "/api/v1/admin/feeds/:id",
    handler: ({ params, body }) => {
      const type = "POST";
      if (typeof body?.hidden !== "boolean") throw new HttpError(400, "INVALID_INPUT", "숨김 여부를 확인해 주세요.");
      const validation = reasonError(String(body?.reason ?? ""), body.hidden);
      if (validation) throw new HttpError(400, "INVALID_INPUT", validation);
      const exists = db.posts.some((p) => p.id === params.id);
      if (!exists) notFound("콘텐츠");
      const key = `${type}:${params.id}`;
      if (body?.hidden) hidden.add(key);
      else hidden.delete(key);
      return contentList(type, "").find((c) => c.id === params.id);
    },
  },

  // ── 크리에이터 심사 ────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/creator-applications",
    handler: ({ query }) => {
      const status = query.get("status") ?? "PENDING";
      return creatorState.applications
        .filter((a) => (status === "PENDING" ? a.status === "PENDING" : a.status !== "PENDING"))
        .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
        .map(({ userId, ...a }) => ({ ...a, applicant: userSummary(userId) }));
    },
  },
  {
    method: "POST",
    path: "/api/v1/admin/creator-applications/:id/review",
    handler: ({ params, body }) => {
      const { userId, ...a } = reviewApplication(params.id, !!body?.approve, String(body?.rejectReason ?? ""));
      return { ...a, applicant: userSummary(userId) };
    },
  },

  // ── 환불 처리 ──────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/refunds",
    handler: ({ query }) => {
      const status = query.get("status") ?? "PENDING";
      return db.purchases
        .filter((p) => (status === "PENDING" ? p.status === "REFUND_REQUESTED" : p.status === "REFUNDED" || p.status === "REFUND_REJECTED"))
        .sort((a, b) => (b.refund?.requestedAt ?? "").localeCompare(a.refund?.requestedAt ?? ""))
        .map(purchaseDto);
    },
  },
  {
    /** 승인하면 카드 결제를 취소(mock)하고 열람 권한을 회수합니다. */
    method: "POST",
    path: "/api/v1/admin/refunds/:purchaseId",
    delay: 500,
    handler: ({ params, body }) => {
      const p = db.purchases.find((x) => x.id === params.purchaseId) ?? notFound("구매");
      if (p.status !== "REFUND_REQUESTED" || !p.refund) throw new HttpError(409, "NOT_REQUESTED", "환불 신청 상태가 아니에요.");
      const approve = !!body?.approve;
      const rejectReason = String(body?.rejectReason ?? "").trim();
      if (!approve && !rejectReason) throw new HttpError(400, "INVALID_INPUT", "거절 사유를 입력해 주세요.");
      p.status = approve ? "REFUNDED" : "REFUND_REJECTED";
      p.refund = { ...p.refund, processedAt: new Date().toISOString(), rejectReason: approve ? null : rejectReason };
      return purchaseDto(p);
    },
  },

  // ── 정산 처리 ──────────────────────────────────────
  {
    method: "GET",
    path: "/api/v1/admin/settlements",
    handler: ({ query }) => {
      const status = query.get("status") ?? "SCHEDULED";
      return creatorState.settlements
        .filter((s) => (status === "SCHEDULED" ? s.status !== "PAID" : s.status === "PAID"))
        .sort((a, b) => b.period.localeCompare(a.period))
        .map(({ userId, ...s }) => ({ ...s, creator: userSummary(userId), account: accountDto(userId) }));
    },
  },
  {
    /** 지급 완료 / 보류 / 보류 해제. 계좌가 없으면 지급할 수 없습니다. */
    method: "POST",
    path: "/api/v1/admin/settlements/:id",
    handler: ({ params, body }) => {
      const s = creatorState.settlements.find((x) => x.id === params.id) ?? notFound("정산");
      const action = String(body?.action ?? "");
      if (s.status === "PAID") throw new HttpError(409, "ALREADY_PAID", "이미 지급한 정산이에요.");
      if (action === "PAY") {
        if (!creatorState.accounts[s.userId]) throw new HttpError(400, "NO_ACCOUNT", "정산 계좌가 없어 지급할 수 없어요.");
        s.status = "PAID";
        s.paidAt = new Date().toISOString();
      } else if (action === "HOLD") s.status = "HELD";
      else if (action === "RELEASE") s.status = "SCHEDULED";
      else throw new HttpError(400, "INVALID_INPUT", "처리 방법을 확인해 주세요.");
      const { userId, ...rest } = s;
      return { ...rest, creator: userSummary(userId), account: accountDto(userId) };
    },
  },
];
