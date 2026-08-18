package com.ladder.mcmcare.security;

import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.exception.ErrorResponse;
import com.ladder.mcmcare.service.FileUrlSigner;
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
 * /files/** 요청의 서명을 검증한다.
 *
 * 손상 사진 · 인계 사진 · 전자서명은 개인정보이므로 URL 만 안다고 열려서는 안 된다.
 * 프론트가 &lt;img src&gt; 로 표시해 Authorization 헤더를 실을 수 없으므로
 * URL 에 담긴 만료 시각과 서명으로 접근을 통제한다.
 *
 * 서명이 만료되면 사용자가 화면을 새로고침할 때 새 URL 을 받는다.
 */
@RequiredArgsConstructor
public class SignedFileFilter extends OncePerRequestFilter {

    private static final String FILE_PATH = "/files/";

    private final FileUrlSigner signer;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !JwtAuthorizationFilter.pathWithinApplication(request).startsWith(FILE_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = JwtAuthorizationFilter.pathWithinApplication(request);

        boolean valid = signer.verify(
                path,
                request.getParameter(FileUrlSigner.PARAM_EXPIRES),
                request.getParameter(FileUrlSigner.PARAM_SIGNATURE));

        if (!valid) {
            // 파일 존재 여부를 드러내지 않도록 404 로 응답한다
            response.setStatus(ErrorCode.NO_MATCHING_DATA.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    jsonMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NO_MATCHING_DATA)));
            return;
        }

        chain.doFilter(request, response);
    }
}
