package com.pickong.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pickong.common.error.BusinessException;
import com.pickong.common.error.CommonErrorCode;
import com.pickong.common.security.CurrentUser;
import com.pickong.common.security.LoginUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @SuppressWarnings("unused")
    static class Handlers {
        void required(@CurrentUser LoginUser user) {
        }

        void optional(@CurrentUser(required = false) LoginUser viewer) {
        }

        void notAnnotated(LoginUser user) {
        }
    }

    private static MethodParameter param(String method) throws NoSuchMethodException {
        return new MethodParameter(Handlers.class.getDeclaredMethod(method, LoginUser.class), 0);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("@CurrentUser가 붙은 LoginUser 파라미터만 처리한다")
    void supports() throws Exception {
        assertThat(resolver.supportsParameter(param("required"))).isTrue();
        assertThat(resolver.supportsParameter(param("notAnnotated"))).isFalse();
    }

    @Test
    @DisplayName("로그인했으면 인증 필터가 넣어 둔 회원을 돌려준다")
    void authenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new LoginUser(7L), null, List.of()));

        assertThat(resolver.resolveArgument(param("required"), null, null, null)).isEqualTo(new LoginUser(7L));
    }

    @Test
    @DisplayName("로그인하지 않았으면 required=true는 401, required=false는 null")
    void anonymous() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(param("required"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThat(resolver.resolveArgument(param("optional"), null, null, null)).isNull();
    }
}
