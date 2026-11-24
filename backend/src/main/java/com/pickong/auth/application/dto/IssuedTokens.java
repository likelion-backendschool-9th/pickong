package com.pickong.auth.application.dto;

/**
 * 발급된 토큰 쌍. 웹 어댑터가 받아서 httpOnly 쿠키로 내려주고, 응답 본문에는 담지 않습니다.
 *
 * @param accessToken  Access Token(JWT, 15분)
 * @param refreshToken Refresh Token 원문(14일). DB에는 해시만 있고 원문은 이 순간에만 존재합니다
 */
public record IssuedTokens(String accessToken, String refreshToken) {

    /** 실수로 로그에 찍혀도 토큰이 노출되지 않게 합니다. */
    @Override
    public String toString() {
        return "IssuedTokens[***]";
    }
}
