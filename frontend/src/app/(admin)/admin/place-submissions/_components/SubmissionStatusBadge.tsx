import type { PlaceSubmissionStatus } from "@/entities/place-submission";
import { Badge } from "@/shared/ui/display";

const LABEL: Record<PlaceSubmissionStatus, string> = {
  PENDING: "심사 대기",
  APPROVED: "승인 완료",
  REJECTED: "반려",
};

const TONE = {
  PENDING: "warning",
  APPROVED: "lime",
  REJECTED: "danger",
} as const;

export function SubmissionStatusBadge({ status }: { status: PlaceSubmissionStatus }) {
  return <Badge tone={TONE[status]}>{LABEL[status]}</Badge>;
}
