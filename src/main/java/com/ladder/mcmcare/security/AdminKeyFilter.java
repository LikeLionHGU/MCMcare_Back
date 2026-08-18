package com.ladder.mcmcare.security;

import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /api/admin/** 는 X-Admin-Key 헤더로 보호한다.
 *
 * 배포 URL 이 행사 종료까지 공개 상태로 유지되므로, 무인증으로 두면
 * 접수번호만 알아도 타인의 AS 상태를 변경할 수 있다.
 */
@RequiredArgsConstructor
public class AdminKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Admin-Key";
    private static final String ADMIN_PATH = "/api/admin";

    private final String adminKey;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // contextPath 를 제외한 경로로 판정한다.
        // getRequestURI() 는 contextPath 를 포함하므로 서브 경로 배포 시 필터가 통째로 스킵된다.
        return !JwtAuthorizationFilter.pathWithinApplication(request).startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!matches(request.getHeader(HEADER))) {
            response.setStatus(ErrorCode.NO_PERMISSION.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    jsonMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NO_PERMISSION)));
            return;
        }

        chain.doFilter(request, response);
    }

    /** 상수 시간 비교 — 문자열 equals 는 앞에서부터 비교해 길이·일치 정도가 응답 시간에 드러난다. */
    private boolean matches(String provided) {
        if (provided == null || adminKey == null || adminKey.isBlank()) return false;
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                adminKey.getBytes(StandardCharsets.UTF_8));
    }
}
