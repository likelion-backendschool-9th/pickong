/** className 조합. falsy 값은 무시합니다. */
export function cn(...values: (string | false | null | undefined)[]) {
  return values.filter(Boolean).join(" ");
}
