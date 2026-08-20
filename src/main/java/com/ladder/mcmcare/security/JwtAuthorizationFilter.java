package com.ladder.mcmcare.security;

import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.exception.ErrorResponse;
import com.ladder.mcmcare.repository.MemberRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Authorization: Bearer {token} 을 읽어 SecurityContext 를 채운다.
 * 토큰이 없으면 그냥 통과시키고, 접근 제어는 SecurityConfig / @PreAuthorize 가 담당한다.
 */
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /**
     * 탈퇴 전에 발급된 JWT 는 서명은 유효하지만 사용을 막아야 한다.
     * JwtProvider.parse() 가 DB 를 조회하지 않으므로 필터에서 직접 확인한다.
     * 탈퇴 시점 이후 토큰 만료 시각까지의 공백을 이렇게 막는다.
     */
    private final MemberRepository memberRepository;

    /**
     * 인증 없이 접근하는 경로.
     * 만료된 토큰을 들고 있어도 재로그인할 수 있어야 하므로 이 경로들은 토큰을 검사하지 않는다.
     * 검사하면 만료 토큰 보유 시 로그인 자체가 401 로 막혀 복구가 불가능해진다.
     */
    private static final List<String> SKIP_PATHS = List.of(
            "/api/health",
            "/api/member/signup",
            "/api/member/login",
            "/api/member/login/google",
            "/api/driver/login",
            "/files/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    );

    private final JwtProvider jwtProvider;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return SKIP_PATHS.stream().anyMatch(p -> matcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                PrincipalDetails principal = jwtProvider.parse(token);
                if (principal != null) {
                    // 탈퇴 계정의 토큰은 서명이 유효해도 거부한다.
                    // 탈퇴 직전 발급된 토큰이 만료 전까지 살아있는 것을 막기 위함이다.
                    boolean withdrawn = memberRepository.findById(principal.getId())
                            .map(m -> m.isWithdrawn())
                            .orElse(true);  // 계정 자체가 없으면 거부
                    if (withdrawn) {
                        writeError(response, ErrorCode.WITHDRAWN_MEMBER);
                        return;
                    }

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                writeError(response, ErrorCode.TOKEN_EXPIRED);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** contextPath 를 제외한 경로. 서브 경로 배포 시에도 매칭이 어긋나지 않는다. */
    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            String rest = uri.substring(ctx.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return uri;
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(code)));
    }
}
