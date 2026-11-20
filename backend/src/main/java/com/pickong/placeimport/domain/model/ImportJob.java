package com.pickong.placeimport.domain.model;

import com.pickong.placeimport.domain.vo.ImportJobStatus;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 장소 가져오기 작업 애그리거트. 끝난 작업은 바뀌지 않습니다.
 * <p>result는 최종 추출 결과 요약(JSON)입니다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImportJob {
    private Long id;
    private Long userId;
    private String url;
    private ImportJobStatus status;
    private String result;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
