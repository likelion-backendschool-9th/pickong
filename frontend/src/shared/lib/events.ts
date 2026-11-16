/**
 * 화면 사이 신호 (window CustomEvent).
 * 하단바의 홈 버튼을 피드에서 다시 누르면 피드가 맨 위로 올라가 새로고침합니다.
 */
export const HOME_RESELECT_EVENT = "pickong:home-reselect";

export const emitHomeReselect = () => window.dispatchEvent(new Event(HOME_RESELECT_EVENT));
