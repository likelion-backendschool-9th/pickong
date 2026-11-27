package com.pickong.media.adapter.out.external.r2;

import com.pickong.media.application.port.out.ObjectStoragePort;
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

    private final S3Client client;
    private final S3Presigner presigner;
    private final R2Properties properties;

    public R2StorageAdapter(S3Client client, S3Presigner presigner, R2Properties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * Presigned URL을 만듦. R2를 부르지 않고 비밀키로 서명만 계산함
     * 파일 이름, 종류, 크기가 서명에 들어가서 브라우저가 다른 종류나 크기로 올리면 R2가 거부함
     */
    @Override
    public String createUploadUrl(String key, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        return presigner.presignPutObject(request -> request
                        .signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(put))
                .url()
                .toString();
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
