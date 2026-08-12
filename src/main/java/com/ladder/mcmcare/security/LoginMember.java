package com.ladder.mcmcare.security;

import java.lang.annotation.*;

/**
 * 컨트롤러에서 로그인 주체를 받는다.
 *
 * <pre>
 * public ResponseEntity&lt;HomeResDto&gt; home(@LoginMember PrincipalDetails principal)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginMember {
}
