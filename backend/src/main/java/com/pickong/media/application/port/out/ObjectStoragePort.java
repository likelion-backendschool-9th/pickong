package com.pickong.media.application.port.out;

import java.util.Map;
import java.util.Optional;

/**
 * 오브젝트 스토리지(R2) out-port
 * 서비스는 이 인터페이스만 알고, 실제 R2 호출은 R2StorageAdapter가 함
 */
public interface ObjectStoragePort {

    /**
     * 필수 설정값(계정 ID, 키, 버킷, 공개 URL)이 모두 있는지
     * 없어도 앱은 시작하지만, 이때 Presigned URL을 발급하면 실제로는 쓸 수 없는 URL이 나가므로 발급 전에 확인함
     */
    boolean isConfigured();

    /**
     * 업로드용 Presigned PUT URL을 만들어 돌려줌
     * 이 URL로는 지정한 key에, 유효 시간 동안, 서명된 Content-Type과 Content-Length의 파일만,
     * key에 객체가 아직 없을 때 한 번만 올릴 수 있음
     */
    UploadUrl createUploadUrl(String key, String contentType, long contentLength);

    /** R2에 key 파일이 있으면 크기와 종류를 돌려줌, 없으면 빈 값 */
    Optional<StoredObject> findObject(String key);

    /** R2에서 key 파일을 지움, 이미 없어도 오류를 내지 않음. 네트워크나 권한 오류면 런타임 예외를 냄 */
    void deleteObject(String key);

    /** key 파일을 브라우저에서 볼 수 있는 공개 URL (서명 없이 열림) */
    String publicUrl(String key);

    /**
     * Presigned PUT URL과, PUT 요청에 그대로 넣어야 하는 헤더
     *
     * @param headers 서명에 들어간 헤더라서 값이 다르거나 빠지면 R2가 403으로 거부함
     */
    record UploadUrl(String url, Map<String, String> headers) {
    }

    /** R2에 실제로 저장된 파일 정보 (HEAD 응답) */
    record StoredObject(long contentLength, String contentType) {
    }
}
