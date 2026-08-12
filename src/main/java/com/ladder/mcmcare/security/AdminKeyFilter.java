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

/**
 * /api/admin/** 는 X-Admin-Key 헤더로 보호한다.
 * 배포 URL 이 행사 종료까지 공개 상태로 유지되므로 무인증으로 두면
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
        return !request.getRequestURI().startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!adminKey.equals(request.getHeader(HEADER))) {
            response.setStatus(ErrorCode.NO_PERMISSION.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    jsonMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NO_PERMISSION)));
            return;
        }

        chain.doFilter(request, response);
    }
}
