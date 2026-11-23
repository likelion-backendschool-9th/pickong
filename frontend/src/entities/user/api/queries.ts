import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { useDebounced } from "@/shared/lib/useDebounced";
import { nicknameError, nicknameStatus, type NicknameStatus } from "../nickname";
import { userApi } from "./userApi";

export const userKeys = {
  all: ["users"] as const,
  me: () => [...userKeys.all, "me"] as const,
  detail: (username: string) => [...userKeys.all, "detail", username] as const,
  followers: (username: string) => [...userKeys.all, "followers", username] as const,
  following: (username: string) => [...userKeys.all, "following", username] as const,
  search: (q: string) => [...userKeys.all, "search", q] as const,
  nicknameAvailability: (nickname: string) => [...userKeys.all, "nickname-availability", nickname] as const,
};

/**
 * 닉네임 입력칸의 실시간 확인. 입력이 멈추면(300ms) 형식을 검사하고 서버에 중복 여부를 묻습니다.
 * @param current 지금 쓰고 있는 닉네임(프로필 편집). 같은 값이면 묻지 않고 "current"
 */
export function useNicknameCheck(nickname: string, current?: string): NicknameStatus {
  const settled = useDebounced(nickname, 300);
  const checkable = settled !== "" && settled !== current && !nicknameError(settled);
  const availability = useQuery({
    queryKey: userKeys.nicknameAvailability(settled),
    queryFn: ({ signal }) => userApi.nicknameAvailability(settled, signal),
    enabled: checkable,
    staleTime: 30_000,
    // 입력칸 옆 표시가 오래 돌지 않도록 실패하면 바로 알려 줍니다.
    retry: false,
  });
  return nicknameStatus(nickname, settled, availability.isError ? null : availability.data?.available, current);
}

/** 사용자 검색. 검색어가 비어 있으면 요청하지 않습니다. */
export const useUserSearch = (q: string) =>
  useQuery({
    queryKey: userKeys.search(q),
    queryFn: ({ signal }) => userApi.search(q, signal),
    enabled: q.trim().length > 0,
    staleTime: 60_000,
  });

export const useMe = () => useQuery({ queryKey: userKeys.me(), queryFn: userApi.me, staleTime: 5 * 60_000 });

export const useUser = (username: string) =>
  useQuery({ queryKey: userKeys.detail(username), queryFn: () => userApi.get(username) });

export const useFollowers = (username: string) =>
  useQuery({ queryKey: userKeys.followers(username), queryFn: () => userApi.followers(username) });

export const useFollowing = (username: string) =>
  useQuery({ queryKey: userKeys.following(username), queryFn: () => userApi.following(username) });

export function useUpdateMe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: userApi.updateMe,
    onSuccess: (me) => {
      qc.setQueryData(userKeys.me(), me);
      qc.invalidateQueries({ queryKey: userKeys.detail(me.username) });
    },
    onError: (error, input) => {
      // 확인 뒤 다른 사람이 먼저 가져간 경우: 입력칸 옆 표시도 ‘사용 중’으로 바꿉니다.
      if (error instanceof ApiError && error.code === "USERNAME_TAKEN") {
        qc.setQueryData(userKeys.nicknameAvailability(input.username), { available: false });
      }
    },
  });
}
