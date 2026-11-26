package com.pickong.moderation.adapter.in.web.admin;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.error.ErrorResponse;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.CurrentAdmin;
import com.pickong.moderation.application.port.in.SanctionUseCase;
import com.pickong.moderation.application.port.in.UserAdminQueryUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 목록과 직접 제재. 인증·인가·CSRF는 기존 보안 체인을 유지합니다. */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final UserAdminQueryUseCase users;
    private final SanctionUseCase sanctions;

    public AdminUserController(UserAdminQueryUseCase users, SanctionUseCase sanctions) {
        this.users = users;
        this.sanctions = sanctions;
    }

    @GetMapping
    public List<AdminUserResponse> list(@CurrentAdmin AdminPrincipal admin,
            @RequestParam(required = false) String q, @RequestParam(required = false) String filter) {
        return users.list(q, filter).stream().map(AdminUserResponse::from).toList();
    }

    @PostMapping("/{userId}/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse.SanctionResponse sanction(@CurrentAdmin AdminPrincipal admin,
            @PathVariable String userId, @RequestBody(required = false) CreateSanctionRequest request) {
        CreateSanctionRequest body = request == null ? new CreateSanctionRequest(null, null) : request;
        return AdminUserResponse.SanctionResponse.from(sanctions.sanction(body.toCommand(identifier(userId), admin.adminId())));
    }

    @DeleteMapping("/{userId}/sanctions/{sanctionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@CurrentAdmin AdminPrincipal admin, @PathVariable String userId, @PathVariable String sanctionId,
            @RequestBody(required = false) RevokeSanctionRequest request) {
        sanctions.revoke(identifier(userId), identifier(sanctionId), admin.adminId(), request == null ? null : request.reason());
    }

    /** 잘못된 JSON도 이 API의 공통 오류 형식을 유지합니다. 다른 컨트롤러의 처리는 바꾸지 않습니다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse unreadableBody(HttpMessageNotReadableException exception) {
        return ErrorResponse.of(CommonErrorCode.INVALID_INPUT);
    }

    private static long identifier(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
