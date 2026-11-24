package com.pickong.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 로그인 회원({@link LoginUser})을 넣어 줍니다.
 * <pre>{@code
 * @PostMapping("/feeds")
 * FeedResponse create(@CurrentUser LoginUser user, @RequestBody @Valid CreateFeedRequest request)
 *
 * // 로그인하지 않아도 되는 화면(공개 피드 등): 비로그인이면 null
 * @GetMapping("/feeds/{feedId}")
 * FeedResponse get(@CurrentUser(required = false) LoginUser viewer, @PathVariable Long feedId)
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {

    /** true(기본)면 로그인하지 않은 요청은 401 UNAUTHORIZED, false면 null을 넣습니다. */
    boolean required() default true;
}
