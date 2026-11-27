package com.pickong.media.adapter.out.external.r2;

import static org.assertj.core.api.Assertions.assertThat;

import com.pickong.media.application.port.out.ObjectStoragePort.UploadUrl;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Presigned URL 형식만 확인함. 서명은 R2에 접속하지 않고 계산하므로 가짜 키로도 만들어짐
 * 실제 업로드가 되는지는 진짜 키가 있는 로컬에서 직접 확인해야 됨
 */
class R2StorageAdapterTest {

    private static final String ACCOUNT = "0123456789abcdef0123456789abcdef";

    private final R2Properties properties = new R2Properties(ACCOUNT, "test-access-key", "test-secret-key",
            "pickong-test", "https://pub-test.r2.dev/", Duration.ofMinutes(5));
    private final R2ClientConfig config = new R2ClientConfig();
    private final R2StorageAdapter adapter = new R2StorageAdapter(
            config.r2Client(properties), config.r2Presigner(properties), properties);

    @Test
    @DisplayName("Presigned URL: 계정 주소/버킷/파일 이름, 5분(300초) 유효, Content-Type과 If-None-Match가 서명에 들어감")
    void createUploadUrl() {
        UploadUrl upload = adapter.createUploadUrl("feeds/abc.jpg", "image/jpeg", 2048);
        URI url = URI.create(upload.url());

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo(ACCOUNT + ".r2.cloudflarestorage.com");
        assertThat(url.getPath()).isEqualTo("/pickong-test/feeds/abc.jpg");
        assertThat(url.getQuery()).contains("X-Amz-Expires=300");
        assertThat(url.getQuery()).contains("X-Amz-Signature=");
        assertThat(url.getQuery()).containsPattern("X-Amz-SignedHeaders=[^&]*content-type");
        assertThat(url.getQuery()).containsPattern("X-Amz-SignedHeaders=[^&]*if-none-match");
        // 브라우저가 PUT에 넣어야 하는 헤더. Host와 Content-Length는 브라우저가 자동으로 넣어서 빠짐
        assertThat(upload.headers()).containsOnly(
                Map.entry("Content-Type", "image/jpeg"), Map.entry("If-None-Match", "*"));
        // 비밀키는 주소에 들어가지 않음 (Access Key만 Credential에 들어감)
        assertThat(url.toString()).doesNotContain("test-secret-key");
    }

    @Test
    @DisplayName("설정 완료 여부: 다섯 값이 모두 있으면 true, 하나라도 비면 false")
    void isConfigured() {
        assertThat(adapter.isConfigured()).isTrue();
        assertThat(new R2Properties(ACCOUNT, "a", "b", "bucket", " ", null).isConfigured()).isFalse();
        assertThat(new R2Properties(ACCOUNT, "", "b", "bucket", "https://pub", null).isConfigured()).isFalse();
        assertThat(new R2Properties(null, null, null, null, null, null).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("공개 주소: 설정 끝의 /를 떼고 파일 이름을 붙임")
    void publicUrl() {
        assertThat(adapter.publicUrl("feeds/abc.jpg")).isEqualTo("https://pub-test.r2.dev/feeds/abc.jpg");
    }

    @Test
    @DisplayName(".env에 빈 줄(R2_ACCOUNT_ID=)이 있어도 클라이언트를 만들 수 있음 (키 없는 팀원의 앱 시작 보장)")
    void blankValuesStillBuildClients() {
        R2Properties blank = new R2Properties(" ", "", null, "", "https://pub", null);

        assertThat(blank.accountId()).isEqualTo(R2Properties.NOT_CONFIGURED);
        assertThat(blank.bucket()).isEqualTo(R2Properties.NOT_CONFIGURED);
        new R2StorageAdapter(config.r2Client(blank), config.r2Presigner(blank), blank);
    }

    @Test
    @DisplayName("Presigned URL 유효 시간을 정하지 않으면 5분")
    void defaultTtl() {
        R2Properties noTtl = new R2Properties(ACCOUNT, "a", "b", "bucket", "https://pub", null);

        assertThat(noTtl.uploadUrlTtl()).isEqualTo(Duration.ofMinutes(5));
    }
}
