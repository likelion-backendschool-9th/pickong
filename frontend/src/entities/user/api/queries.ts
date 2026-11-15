import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { userApi } from "./userApi";

export const userKeys = {
  all: ["users"] as const,
  me: () => [...userKeys.all, "me"] as const,
  detail: (username: string) => [...userKeys.all, "detail", username] as const,
  followers: (username: string) => [...userKeys.all, "followers", username] as const,
  following: (username: string) => [...userKeys.all, "following", username] as const,
};

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
  });
}
