/**
 * 신고 당시 글과 현재 글의 비교.
 * 백엔드 ReportContentComparison과 같은 규칙입니다.
 * Java String.strip()은 NBSP를 공백으로 보지 않으므로 trim()을 쓰지 않습니다.
 */

const NON_BREAKING = new Set([0x00a0, 0x2007, 0x202f]);

function isJavaWhitespace(codePoint: number): boolean {
  if (codePoint <= 0xff) {
    return (codePoint >= 0x09 && codePoint <= 0x0d) || (codePoint >= 0x1c && codePoint <= 0x1f) || codePoint === 0x20;
  }
  if (NON_BREAKING.has(codePoint)) return false;
  if (codePoint === 0x1680 || codePoint === 0x2028 || codePoint === 0x2029 || codePoint === 0x205f || codePoint === 0x3000) {
    return true;
  }
  return codePoint >= 0x2000 && codePoint <= 0x200a;
}

function chars(value: string): string[] {
  return [...value];
}

function javaStrip(value: string): string {
  const units = chars(value);
  let start = 0;
  let end = units.length;
  while (start < end && isJavaWhitespace(units[start].codePointAt(0) ?? 0)) start += 1;
  while (end > start && isJavaWhitespace(units[end - 1].codePointAt(0) ?? 0)) end -= 1;
  return units.slice(start, end).join("");
}

function isJavaBlank(value: string): boolean {
  return chars(value).every((unit) => isJavaWhitespace(unit.codePointAt(0) ?? 0));
}

function normalize(value: string): string {
  return javaStrip(value.replaceAll("\r\n", "\n").replaceAll("\r", "\n"));
}

/** 카드 제목과 기존 첫 줄 비교. 내용이 없으면 null입니다. */
export function excerpt(content: string | null | undefined): string | null {
  if (content == null || isJavaBlank(content)) return null;
  const newline = content.indexOf("\n");
  const line = newline < 0 ? content : content.slice(0, newline);
  const trimmed = javaStrip(line);
  return trimmed === "" ? null : trimmed;
}

/** FULL 본문. null은 누락이고, 빈 문자열은 빈 본문입니다. */
export function compareFull(snapshotText: string | null, currentText: string | null): boolean | null {
  if (snapshotText == null || currentText == null) return null;
  return normalize(snapshotText) !== normalize(currentText);
}

/** 기존 스냅샷과 회원 표시 이름. 첫 줄이 없으면 null이고, 둘째 줄 이후는 보지 않습니다. */
export function compareFirstLine(snapshotText: string | null, currentText: string | null): boolean | null {
  const snapshotLine = excerpt(snapshotText);
  const currentLine = excerpt(currentText);
  if (snapshotLine == null || currentLine == null) return null;
  return snapshotLine !== currentLine;
}
