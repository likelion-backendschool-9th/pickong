"use client";

import { X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { userApi } from "@/entities/user";
import { ApiError, errorMessage } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Avatar } from "@/shared/ui/display";
import { Input } from "@/shared/ui/form";
import { normalizeUsername, pickError, type PickedUser } from "../_lib/group";

/**
 * 아이디로 초대할 사람 추가하기. 입력한 아이디를 프로필 API(GET /users/{username})로 확인한 뒤 목록에 넣습니다.
 * 사람 검색 API가 아직 없어 아이디를 직접 입력합니다.
 *
 * @param taken 고를 수 없는 아이디 (나 · 이미 참여 중인 사람)
 * @param seats 더 고를 수 있는 사람 수 (이미 고른 사람 포함하지 않음)
 */
export function UsernamePicker({
  picked,
  onChange,
  taken,
  seats,
}: {
  picked: PickedUser[];
  onChange: (users: PickedUser[]) => void;
  taken: string[];
  seats: number;
}) {
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const add = async (e: FormEvent) => {
    e.preventDefault();
    const username = normalizeUsername(input);
    const problem = pickError(username, [...taken, ...picked.map((p) => p.username)], seats - picked.length);
    if (problem) return setError(problem);
    setChecking(true);
    try {
      const user = await userApi.get(username);
      if (user.isMe) return setError("나는 이미 참여하고 있어요.");
      onChange([...picked, { username: user.username, profileImageUrl: user.profileImageUrl }]);
      setInput("");
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError && err.isNotFound ? "찾을 수 없는 아이디예요." : errorMessage(err));
    } finally {
      setChecking(false);
    }
  };

  return (
    <div>
      <form onSubmit={add} className="flex gap-2">
        <Input
          value={input}
          onChange={(e) => {
            setInput(e.target.value);
            setError(null);
          }}
          placeholder="초대할 사람의 아이디"
          aria-label="초대할 사람의 아이디"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          className="flex-1"
        />
        <Button type="submit" variant="secondary" loading={checking} disabled={!input.trim()}>
          추가
        </Button>
      </form>
      {error && <p className="mt-1.5 text-[12px] text-danger">{error}</p>}
      {picked.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-2" aria-label="초대할 사람">
          {picked.map((p) => (
            <li key={p.username} className="flex items-center gap-1.5 rounded-full bg-soft py-1 pr-1 pl-1.5">
              <Avatar src={p.profileImageUrl} name={p.username} size={24} />
              <span className="text-[13px] font-bold">{p.username}</span>
              <button
                type="button"
                onClick={() => onChange(picked.filter((x) => x.username !== p.username))}
                aria-label={`${p.username} 빼기`}
                className="inline-flex size-6 items-center justify-center rounded-full text-ink-sub active:bg-selected"
              >
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
