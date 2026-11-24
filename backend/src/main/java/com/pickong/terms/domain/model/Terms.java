package com.pickong.terms.domain.model;

import com.pickong.terms.domain.vo.TermsType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 약관 애그리거트. 버전과 필수 여부, 시행 일시.
 * {@code requiresReconsent}: 이 버전이 시행되면 이전 버전에 한 동의를 더 이상 인정하지 않음 (규칙은 {@link TermsHistory})
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Terms {
    private Long id;
    private TermsType type;
    private String version;
    private String title;
    private String contentUrl;
    private boolean required;
    private OffsetDateTime effectiveAt;
    private boolean requiresReconsent;
    private OffsetDateTime createdAt;
}
