package com.pickong.moderation.adapter.in.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import com.pickong.moderation.adapter.in.web.request.CreateReportRequest;
import com.pickong.moderation.adapter.in.web.response.CreateReportResponse;
import com.pickong.moderation.application.dto.ReportCommand;
import com.pickong.moderation.application.port.in.ReportUseCase;
import com.pickong.moderation.domain.ModerationErrorCode;
import com.pickong.moderation.domain.vo.ReportTargetType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 신고 접수. 경로는 {@code /reports}이고, 서블릿 경로 {@code /api/v1}과 겹치지 않습니다.
 * 신고자 id는 로그인 회원에서만 읽습니다. POST의 CSRF 검사는 기존 보안 설정을 그대로 탑니다.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportUseCase reports;

    public ReportController(ReportUseCase reports) {
        this.reports = reports;
    }

    @PostMapping
    public ResponseEntity<CreateReportResponse> report(@CurrentUser LoginUser user,
            @RequestBody(required = false) CreateReportRequest request) {
        if (user.userId() == null || user.userId() <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (request == null || request.targetId() == null || request.targetId() <= 0) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_TARGET);
        }
        var saved = reports.report(new ReportCommand(
                user.userId(), targetType(request.targetType()), request.targetId(), request.reason(), request.detail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateReportResponse.from(saved));
    }

    /** 화면의 POST와 저장 값 FEED를 같은 게시물로 받습니다. 회원 · 컬렉션은 거절합니다. */
    private static ReportTargetType targetType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ModerationErrorCode.INVALID_REPORT_TARGET);
        }
        return switch (raw.strip()) {
            case "POST", "FEED" -> ReportTargetType.FEED;
            case "COMMENT" -> ReportTargetType.COMMENT;
            default -> throw new BusinessException(ModerationErrorCode.INVALID_REPORT_TARGET);
        };
    }
}
