package com.ladder.mcmcare.config;

import com.ladder.mcmcare.security.LoginMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CORS 는 SecurityConfig 에서 설정한다.
 * Security 필터체인이 MVC 보다 먼저 돌기 때문에 여기서만 설정하면
 * OPTIONS 프리플라이트가 인증 단계에서 401 로 막힌다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginMemberArgumentResolver loginMemberArgumentResolver;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }

    /** 업로드 파일 서빙 (로컬 개발용) */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**").addResourceLocations(uploadLocation());
    }

    /**
     * Path.toUri() 는 디렉터리가 존재할 때만 끝에 "/" 를 붙인다.
     * 없는 상태로 기동하면 "file:/.../uploads" 가 되어
     * "/files/demo/a.jpg" 요청이 "uploadsdemo/a.jpg" 로 결합돼 서빙이 깨진다.
     * 그래서 디렉터리를 미리 만들고, 그래도 없으면 슬래시를 직접 붙인다.
     */
    private String uploadLocation() {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("업로드 디렉터리 생성 실패: {}", dir, e);
        }
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
