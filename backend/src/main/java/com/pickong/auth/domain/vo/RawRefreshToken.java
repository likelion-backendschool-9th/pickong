package com.pickong.auth.domain.vo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh Token 원문. 쿠키로만 오가고 DB에는 {@link #hash()} 값만 저장합니다.
 * <p>
 * Refresh Token은 JWT가 아니라 의미 없는 랜덤 문자열입니다. 서버가 DB에서 찾아봐야만 의미가 생기므로
 * 언제든 폐기할 수 있고(로그아웃 · 재사용 감지), 토큰 자체에 정보가 담기지 않습니다.
 *
 * @param value 쿠키에 담기는 원문 (Base64 URL, 43자)
 */
public record RawRefreshToken(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 256비트. 추측해서 맞히는 것이 사실상 불가능한 길이입니다. */
    private static final int BYTES = 32;

    /** 새 Refresh Token을 만듭니다. 암호학적으로 안전한 난수(SecureRandom)를 씁니다. */
    public static RawRefreshToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new RawRefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /**
     * DB에 저장 · 조회할 때 쓰는 SHA-256 해시(16진수 64자).
     * DB가 유출되어도 해시로는 원래 토큰을 알아낼 수 없어서, 유출된 값으로 로그인할 수 없습니다.
     * (랜덤 256비트라 비밀번호처럼 느린 해시 · 솔트가 필요하지 않습니다)
     */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JDK에 있어서 실제로는 일어나지 않습니다.
            throw new IllegalStateException(e);
        }
    }
}
