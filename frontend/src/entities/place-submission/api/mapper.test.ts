import { describe, expect, it } from "vitest";
import type { PlaceSubmissionDto } from "./dto";
import { toLocation, toPlaceSubmission } from "./mapper";

const dto = (patch: Partial<PlaceSubmissionDto> = {}): PlaceSubmissionDto => ({
  id: "sub-1",
  name: "골목 카페",
  category: "CAFE",
  address: "성수동",
  lat: 37.5446,
  lng: 127.055,
  description: "창가 자리",
  imageUrl: null,
  submitter: { id: "u1", username: "hana", nickname: "하나" },
  submittedAt: "2026-11-20T03:00:00.000Z",
  status: "PENDING",
  reviewedAt: null,
  rejectReason: null,
  placeId: null,
  ...patch,
});

describe("toLocation", () => {
  it("유효한 좌표는 그대로 둔다", () => {
    expect(toLocation(37.5, 127)).toEqual({ lat: 37.5, lng: 127 });
  });

  it("비어 있거나 범위 밖이면 null", () => {
    expect(toLocation(null, 127)).toBeNull();
    expect(toLocation(91, 0)).toBeNull();
    expect(toLocation(0, 181)).toBeNull();
    expect(toLocation(Number.NaN, 0)).toBeNull();
  });
});

describe("toPlaceSubmission", () => {
  it("주소와 제보자가 없으면 화면에 보여줄 값으로 채운다", () => {
    const submission = toPlaceSubmission(dto({ address: null, description: null, submitter: null, lat: null, lng: null }));
    expect(submission.address).toBe("");
    expect(submission.description).toBe("");
    expect(submission.location).toBeNull();
    expect(submission.submitter.nickname).toBe("알 수 없는 사용자");
  });
});
