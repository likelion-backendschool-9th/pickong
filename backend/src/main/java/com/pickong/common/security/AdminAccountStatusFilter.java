package com.pickong.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 인증된 관리자 주체의 계정 상태와 권한을 매 요청 다시 확인합니다. */
public class AdminAccountStatusFilter extends OncePerRequestFilter {
    private final AdminAccessVerifier admins;
    public AdminAccountStatusFilter(AdminAccessVerifier admins) { this.admins = admins; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            boolean role = authentication.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("ROLE_ADMIN"));
            if (authentication.getPrincipal() instanceof AdminPrincipal admin) {
                if (!authentication.isAuthenticated() || !role || admin.adminId() == null || !admins.isActive(admin.adminId())) {
                    SecurityContextHolder.clearContext();
                }
            } else if (role) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
