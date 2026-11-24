package com.pickong.moderation.adapter.in.web.admin;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.CurrentAdmin;
import com.pickong.moderation.application.port.in.ReportAdminUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 목록과 기각.
 * <p>
 * 목록은 배열 전체입니다. 상태 필터와 정렬은 서버가 하고, page · size는 받지 않습니다.
 * 처리자 id는 {@link AdminPrincipal}에서만 읽습니다.
 * POST는 기존 CSRF 검사를 그대로 탑니다. 토큰이 없으면 403 INVALID_CSRF_TOKEN입니다.
 * 상세 조회 API는 화면이 호출하지 않으므로 만들지 않습니다.
 */
@RestController
@RequestMapping("/admin/reports")
public class AdminReportController {

    private final ReportAdminUseCase reports;

    public AdminReportController(ReportAdminUseCase reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<ReportResponse> list(@CurrentAdmin AdminPrincipal admin, @RequestParam(required = false) String status) {
        return reports.list(status).stream().map(ReportResponse::from).toList();
    }

    @PostMapping("/{id}/resolve")
    public ReportResponse resolve(@CurrentAdmin AdminPrincipal admin, @PathVariable String id,
            @RequestBody(required = false) ResolveReportRequest request) {
        ResolveReportRequest body = request == null ? new ResolveReportRequest(null, null, null) : request;
        return ReportResponse.from(reports.resolve(reportId(id), admin.adminId(), body.action(), body.memo(), body.sanctionType()));
    }

    private static long reportId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
