import { ALL_CATEGORIES, type PlaceCategory } from "@/entities/place";
import type { PlaceSubmission, PlaceSubmissionStatus } from "@/entities/place-submission";

export type SubmissionStatusFilter = "ALL" | PlaceSubmissionStatus;
export type SubmissionSort = "newest" | "oldest";

export interface SubmissionQuery {
  status: SubmissionStatusFilter;
  name: string;
  submitter: string;
  category: PlaceCategory | "ALL";
  sort: SubmissionSort;
}

const STATUSES = new Set<SubmissionStatusFilter>(["ALL", "PENDING", "APPROVED", "REJECTED"]);
const SORTS = new Set<SubmissionSort>(["newest", "oldest"]);

export function parseSubmissionQuery(params: Pick<URLSearchParams, "get">): SubmissionQuery {
  const status = params.get("status") ?? "ALL";
  const sort = params.get("sort") ?? "newest";
  const category = params.get("category") ?? "ALL";
  return {
    status: STATUSES.has(status as SubmissionStatusFilter) ? (status as SubmissionStatusFilter) : "ALL",
    name: params.get("q") ?? "",
    submitter: params.get("submitter") ?? "",
    category: category !== "ALL" && ALL_CATEGORIES.includes(category as PlaceCategory) ? (category as PlaceCategory) : "ALL",
    sort: SORTS.has(sort as SubmissionSort) ? (sort as SubmissionSort) : "newest",
  };
}

/** 기본값(전체 · 최신순)은 주소에서 빼서 링크를 짧게 유지합니다. */
export function submissionSearchParams(query: SubmissionQuery): string {
  const params = new URLSearchParams();
  if (query.status !== "ALL") params.set("status", query.status);
  const name = query.name.trim();
  const submitter = query.submitter.trim();
  if (name) params.set("q", name);
  if (submitter) params.set("submitter", submitter);
  if (query.category !== "ALL") params.set("category", query.category);
  if (query.sort === "oldest") params.set("sort", "oldest");
  return params.toString();
}

export function countByStatus(items: PlaceSubmission[]) {
  return {
    all: items.length,
    pending: items.filter((item) => item.status === "PENDING").length,
    approved: items.filter((item) => item.status === "APPROVED").length,
    rejected: items.filter((item) => item.status === "REJECTED").length,
  };
}

export function filterSubmissions(items: PlaceSubmission[], query: SubmissionQuery): PlaceSubmission[] {
  const name = query.name.trim().toLowerCase();
  const submitter = query.submitter.trim().toLowerCase();
  return items
    .filter((item) => query.status === "ALL" || item.status === query.status)
    .filter((item) => !name || item.name.toLowerCase().includes(name))
    .filter((item) => {
      if (!submitter) return true;
      return `${item.submitter.nickname} ${item.submitter.username}`.toLowerCase().includes(submitter);
    })
    .filter((item) => query.category === "ALL" || item.category === query.category)
    .sort((a, b) => {
      const diff = a.submittedAt.localeCompare(b.submittedAt);
      return query.sort === "oldest" ? diff : -diff;
    });
}
