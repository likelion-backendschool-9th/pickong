package com.pickong.common.error;

/**
 * 오류 코드 공통 인터페이스. 각 모듈은 이 인터페이스를 구현한 enum을 자기 domain에 둡니다.
 * (예: {@code feed.domain.FeedErrorCode}, 공통 오류는 {@link CommonErrorCode})
 * <p>
 * 응답의 {@code code}는 enum 상수 이름 그대로입니다. 프론트는 HTTP 상태와 이 code로 분기합니다.
 */
public interface ErrorCode {

    /** HTTP 상태 코드 (400, 401, 403, 404, 409 …) */
    int getStatus();

    /** 사용자에게 그대로 보여 줄 문장 */
    String getMessage();

    /** enum이 구현하면 자동으로 채워집니다. */
    String name();

    /** 응답의 code 값. enum 상수 이름(예: FEED_NOT_FOUND)을 그대로 씁니다. */
    default String getCode() {
        return name();
    }
}
