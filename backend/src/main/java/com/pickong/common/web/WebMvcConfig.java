package com.pickong.common.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 웹 공통 설정: {@code @CurrentUser} 리졸버 등록. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 컨트롤러 파라미터 리졸버 목록에 @CurrentUser 리졸버를 추가합니다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
        resolvers.add(new CurrentAdminArgumentResolver());
    }
}
