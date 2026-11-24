package com.pickong.common.web;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@code @CurrentUser LoginUser} 파라미터에 인증 필터가 넣어 둔 로그인 회원을 꺼내 줍니다. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 이 리졸버가 처리할 파라미터인지 판단합니다. Spring MVC가 컨트롤러 파라미터마다 물어봅니다.
     * {@code @CurrentUser}가 붙은 {@code LoginUser} 타입 파라미터일 때만 true.
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && LoginUser.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 파라미터에 넣을 값을 만듭니다.
     * <ul>
     *   <li>인증 필터가 로그인 회원을 넣어 두었으면 → 그 LoginUser</li>
     *   <li>비로그인 + {@code required = true}(기본) → 401 UNAUTHORIZED</li>
     *   <li>비로그인 + {@code required = false} → null (공개 화면에서 '보는 사람'이 없을 때)</li>
     * </ul>
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        if (parameter.getParameterAnnotation(CurrentUser.class).required()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return null;
    }
}
