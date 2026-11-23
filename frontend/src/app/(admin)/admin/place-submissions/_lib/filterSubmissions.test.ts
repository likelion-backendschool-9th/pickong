import { describe, expect, it } from "vitest";
import type { PlaceSubmission } from "@/entities/place-submission";
import { filterSubmissions, parseSubmissionQuery, type SubmissionQuery } from "./filterSubmissions";

function submission(patch: Partial<PlaceSubmission> & Pick<PlaceSubmission, "id" | "name" | "status" | "submittedAt">): PlaceSubmission {
  return {
    category: "CAFE",
    address: "서울",
    location: { lat: 37.5, lng: 127 },
    description: "",
    imageUrl: null,
    submitter: { id: "u1", username: "hana", nickname: "하나" },
    reviewedAt: null,
    rejectReason: null,
    placeId: null,
    ...patch,
  };
}

const items = [
  submission({ id: "1", name: "성수 골목", status: "PENDING", submittedAt: "2026-11-20T00:00:00.000Z", category: "STREET" }),
  submission({
    id: "2",
    name: "노을 벤치",
    status: "APPROVED",
    submittedAt: "2026-11-22T00:00:00.000Z",
    category: "PHOTO_SPOT",
    submitter: { id: "u2", username: "mino", nickname: "민오" },
  }),
  submission({ id: "3", name: "밤 카페", status: "REJECTED", submittedAt: "2026-11-21T00:00:00.000Z" }),
];

const query = (patch: Partial<SubmissionQuery> = {}): SubmissionQuery => ({
  status: "ALL",
  name: "",
  submitter: "",
  category: "ALL",
  sort: "newest",
  ...patch,
});

describe("parseSubmissionQuery", () => {
  it("없는 값과 알 수 없는 상태는 기본값으로 둔다", () => {
    expect(parseSubmissionQuery(new URLSearchParams("status=NOPE&sort=sideways&category=NOPE"))).toEqual(query());
  });

  it("주소의 검색 조건을 읽는다", () => {
    expect(parseSubmissionQuery(new URLSearchParams("status=PENDING&q=골목&submitter=하나&category=CAFE&sort=oldest"))).toEqual(
      query({ status: "PENDING", name: "골목", submitter: "하나", category: "CAFE", sort: "oldest" }),
    );
  });
});

describe("filterSubmissions", () => {
  it("상태 · 장소명 · 제보자 · 카테고리로 거르고 오래된순으로 정렬한다", () => {
    expect(filterSubmissions(items, query({ status: "PENDING" })).map((item) => item.id)).toEqual(["1"]);
    expect(filterSubmissions(items, query({ name: "노을" })).map((item) => item.id)).toEqual(["2"]);
    expect(filterSubmissions(items, query({ submitter: "MINO" })).map((item) => item.id)).toEqual(["2"]);
    expect(filterSubmissions(items, query({ submitter: "하나" })).map((item) => item.id)).toEqual(["3", "1"]);
    expect(filterSubmissions(items, query({ category: "PHOTO_SPOT" })).map((item) => item.id)).toEqual(["2"]);
    expect(filterSubmissions(items, query({ sort: "oldest" })).map((item) => item.id)).toEqual(["1", "3", "2"]);
  });

  it("최신순이 기본이다", () => {
    expect(filterSubmissions(items, query()).map((item) => item.id)).toEqual(["2", "3", "1"]);
  });
});
