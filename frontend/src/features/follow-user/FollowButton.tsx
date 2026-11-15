"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { userApi, userKeys, type User } from "@/entities/user";
import { Button } from "@/shared/ui/Button";

export function FollowButton({ user, size = "sm", block }: { user: User; size?: "sm" | "md" | "lg"; block?: boolean }) {
  const qc = useQueryClient();
  const toggle = useMutation({
    mutationFn: () => (user.isFollowing ? userApi.unfollow(user.username) : userApi.follow(user.username)),
    onSuccess: (updated) => {
      qc.setQueryData(userKeys.detail(updated.username), updated);
      qc.invalidateQueries({ queryKey: userKeys.all });
      qc.invalidateQueries({ queryKey: ["posts", "feed"] });
    },
  });

  if (user.isMe) return null;
  return (
    <Button
      size={size}
      block={block}
      variant={user.isFollowing ? "outline" : "primary"}
      loading={toggle.isPending}
      onClick={() => toggle.mutate()}
      aria-pressed={user.isFollowing}
    >
      {user.isFollowing ? "팔로잉" : "팔로우"}
    </Button>
  );
}
