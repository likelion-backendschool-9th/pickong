package com.pickong.auth.adapter.in.security;

import com.pickong.common.error.ErrorCode;
import com.pickong.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터 단계(컨트롤러 도달 전)의 오류를 공통 오류 형식으로 씁니다.
 * 필터에서 난 오류는 @RestControllerAdvice(GlobalExceptionHandler)를 거치지 않기 때문에 따로 둡니다.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 응답에 오류 코드에 맞는 상태(401 · 403 등)와 JSON 본문을 씁니다.
     * 예: {@code 401 {"status":401,"code":"UNAUTHORIZED","message":"로그인이 필요해요."}}
     */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode));
    }
}
