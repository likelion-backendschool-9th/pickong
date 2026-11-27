package com.pickong.media.adapter.out.external.r2;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * R2와 통신하는 S3 클라이언트 두 개를 만듦
 * S3Client: HEAD 조회, 삭제처럼 서버가 직접 R2를 부를 때 씀
 * S3Presigner: 브라우저에 내려줄 Presigned URL을 만들 때 씀 (R2를 부르지 않고 서명만 계산함)
 * 만들 때 R2에 접속하지 않아서 키가 없는 로컬이나 테스트에서도 앱이 시작함
 */
@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2ClientConfig {

    // R2는 지역이 없어서 auto를 씀
    private static final Region R2_REGION = Region.of("auto");

    @Bean
    S3Client r2Client(R2Properties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(R2_REGION)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                // 최신 SDK가 기본으로 붙이는 체크섬 헤더를 R2가 지원하지 않는 경우가 있어 필요할 때만 쓰게 함
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    S3Presigner r2Presigner(R2Properties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.endpoint())
                .region(R2_REGION)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyle())
                .build();
    }

    private static StaticCredentialsProvider credentials(R2Properties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
    }

    // 주소를 https://계정ID.r2.cloudflarestorage.com/버킷/파일 모양으로 만듦
    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
