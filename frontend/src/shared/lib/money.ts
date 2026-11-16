/** 원화 표시: 1900 → "1,900원" */
export const formatWon = (amount: number) => `${amount.toLocaleString("ko-KR")}원`;
