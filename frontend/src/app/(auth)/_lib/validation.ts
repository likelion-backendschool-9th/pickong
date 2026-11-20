/** 로그인 · 회원가입 공통 입력 검증. 오류가 없으면 null을 돌려줍니다. */

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailError(email: string): string | null {
  const value = email.trim();
  if (!value) return "이메일을 입력해 주세요.";
  if (!EMAIL_PATTERN.test(value)) return "이메일 형식을 확인해 주세요.";
  return null;
}

export function passwordError(password: string): string | null {
  if (!password) return "비밀번호를 입력해 주세요.";
  if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return "영문·숫자를 포함한 8자 이상으로 설정해 주세요.";
  }
  return null;
}
