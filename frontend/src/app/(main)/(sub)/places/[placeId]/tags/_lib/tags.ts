/** 개인 태그 입력 규칙: 앞의 #과 공백을 빼고, 15자 · 장소당 10개까지, 중복 없이 */

export const TAG_MAX_LENGTH = 15;
export const TAGS_PER_PLACE = 10;

export function normalizeTag(raw: string): string {
  return raw.trim().replace(/^#+/, "").replace(/\s+/g, "");
}

export function addTag(tags: string[], raw: string): { tags: string[]; error: string | null } {
  const tag = normalizeTag(raw);
  if (!tag) return { tags, error: null };
  if (tag.length > TAG_MAX_LENGTH) return { tags, error: `태그는 ${TAG_MAX_LENGTH}자까지 쓸 수 있어요.` };
  if (tags.includes(tag)) return { tags, error: "이미 단 태그예요." };
  if (tags.length >= TAGS_PER_PLACE) return { tags, error: `태그는 장소마다 ${TAGS_PER_PLACE}개까지 달 수 있어요.` };
  return { tags: [...tags, tag], error: null };
}
