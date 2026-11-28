package com.pickong.media.adapter.out.external.r2;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 pickong.media.r2 설정 (값은 backend/.env 또는 환경변수)
 *
 * @param accountId       Cloudflare 계정 ID (32자). 업로드 주소 https://계정ID.r2.cloudflarestorage.com을 만들 때 씀
 * @param accessKeyId     R2 API 토큰의 Access Key
 * @param secretAccessKey R2 API 토큰의 Secret Key. 백엔드에만 있고 브라우저로 내려가지 않음
 * @param bucket          버킷 이름
 * @param publicBaseUrl   사진을 보여 줄 공개 주소. 끝의 /는 떼고 저장함
 * @param uploadUrlTtl    업로드용 Presigned URL 유효 시간 (기본 5분)
 */
@ConfigurationProperties("pickong.media.r2")
public record R2Properties(String accountId, String accessKeyId, String secretAccessKey, String bucket,
        String publicBaseUrl, Duration uploadUrlTtl) {

    /** 키가 없는 팀원도 앱을 띄울 수 있게, 빈 값은 not-configured로 바꿈 (빈 계정 ID면 주소를 못 만들어 시작이 실패함) */
    static final String NOT_CONFIGURED = "not-configured";

    public R2Properties {
        accountId = orNotConfigured(accountId);
        accessKeyId = orNotConfigured(accessKeyId);
        secretAccessKey = orNotConfigured(secretAccessKey);
        bucket = orNotConfigured(bucket);
        publicBaseUrl = orNotConfigured(publicBaseUrl);
        if (uploadUrlTtl == null) {
            uploadUrlTtl = Duration.ofMinutes(5);
        }
        if (publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }

    /** 다섯 값이 모두 채워졌는지. 하나라도 비면 Presigned URL 발급을 막음 */
    public boolean isConfigured() {
        return Stream.of(accountId, accessKeyId, secretAccessKey, bucket, publicBaseUrl)
                .noneMatch(NOT_CONFIGURED::equals);
    }

    private static String orNotConfigured(String value) {
        return value == null || value.isBlank() ? NOT_CONFIGURED : value.trim();
    }

    /** R2의 S3 호환 API 주소 */
    public URI endpoint() {
        return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
    }
}
