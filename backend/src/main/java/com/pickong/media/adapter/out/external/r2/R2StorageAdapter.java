package com.pickong.media.adapter.out.external.r2;

import com.pickong.media.application.port.out.ObjectStoragePort;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** ObjectStoragePort를 Cloudflare R2로 구현함 */
@Component
public class R2StorageAdapter implements ObjectStoragePort {

    // 객체가 하나도 없을 때만 PUT을 허용하는 조건 값
    private static final String IF_NONE_MATCH_ANY = "*";

    private final S3Client client;
    private final S3Presigner presigner;
    private final R2Properties properties;

    public R2StorageAdapter(S3Client client, S3Presigner presigner, R2Properties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Presigned PUT URL을 만듦. R2를 부르지 않고 비밀키로 서명만 계산함
     * 파일 이름, Content-Type, Content-Length, If-None-Match가 서명에 들어감
     * If-None-Match: * 는 key에 객체가 없을 때만 저장하라는 조건이라, 유효 시간 안에 같은 URL로 다시 PUT해도 412로 거부됨
     * (Presigned URL은 일회용이 아니어서 이 조건이 없으면 업로드 확인이 끝난 사진을 덮어쓸 수 있음)
     */
    @Override
    public UploadUrl createUploadUrl(String key, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .ifNoneMatch(IF_NONE_MATCH_ANY)
                .build();
        String url = presigner.presignPutObject(request -> request
                        .signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(put))
                .url()
                .toString();
        // 서명된 헤더 중 브라우저가 직접 넣어야 하는 것만 돌려줌 (Host, Content-Length는 브라우저가 자동으로 넣음)
        return new UploadUrl(url, Map.of("Content-Type", contentType, "If-None-Match", IF_NONE_MATCH_ANY));
    }

    /** HEAD 요청으로 파일 내용은 빼고 크기와 종류만 받아 옴. 없으면 빈 값 */
    @Override
    public Optional<StoredObject> findObject(String key) {
        try {
            HeadObjectResponse head = client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // HEAD 응답에는 본문이 없어서 파일이 없을 때 NoSuchKey 대신 그냥 404로 오는 경우가 있음
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** S3 삭제는 파일이 없어도 성공으로 응답해서 따로 확인하지 않음 */
    @Override
    public void deleteObject(String key) {
        client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }

    @Override
    public String publicUrl(String key) {
        return properties.publicBaseUrl() + "/" + key;
    }
}
