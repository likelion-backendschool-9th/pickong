package com.pickong.common.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.AdminPrincipal;
import com.pickong.common.security.CurrentAdmin;
import com.pickong.common.security.LoginUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@code @CurrentAdmin AdminPrincipal}에 인증된 관리자만 넣습니다. 회원 주체는 거절합니다. */
public class CurrentAdminArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAdmin.class)
                && AdminPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal admin
                && admin.adminId() != null && admin.adminId() > 0) {
            return admin;
        }
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
