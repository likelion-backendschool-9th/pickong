import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { placeKeys } from "@/entities/place";
import { placeSubmissionApi, type SubmitPlaceInput } from "./placeSubmissionApi";

export const placeSubmissionKeys = {
  all: ["place-submissions"] as const,
  list: () => [...placeSubmissionKeys.all, "list"] as const,
  detail: (id: string) => [...placeSubmissionKeys.all, "detail", id] as const,
};

export const usePlaceSubmissions = () =>
  useQuery({ queryKey: placeSubmissionKeys.list(), queryFn: placeSubmissionApi.list });

export const usePlaceSubmission = (id: string) =>
  useQuery({ queryKey: placeSubmissionKeys.detail(id), queryFn: () => placeSubmissionApi.detail(id) });

function useSyncSubmission() {
  const qc = useQueryClient();
  return (id: string) => {
    qc.invalidateQueries({ queryKey: placeSubmissionKeys.detail(id) });
    qc.invalidateQueries({ queryKey: placeSubmissionKeys.list() });
  };
}

export function useApprovePlaceSubmission() {
  const sync = useSyncSubmission();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => placeSubmissionApi.approve(id),
    onSuccess: (submission) => {
      qc.setQueryData(placeSubmissionKeys.detail(submission.id), submission);
      sync(submission.id);
      qc.invalidateQueries({ queryKey: placeKeys.all });
    },
  });
}

/** 제보만 만들고 공개 장소 캐시는 건드리지 않습니다. 승인 후에야 지도 데이터가 바뀝니다. */
export function useSubmitPlace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: SubmitPlaceInput) => placeSubmissionApi.submit(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: placeSubmissionKeys.list() }),
  });
}

export function useRejectPlaceSubmission() {
  const sync = useSyncSubmission();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => placeSubmissionApi.reject(id, reason),
    onSuccess: (submission) => {
      qc.setQueryData(placeSubmissionKeys.detail(submission.id), submission);
      sync(submission.id);
    },
  });
}
