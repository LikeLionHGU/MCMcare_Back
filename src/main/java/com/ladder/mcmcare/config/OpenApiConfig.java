package com.ladder.mcmcare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI  http://localhost:8080/swagger-ui.html
 * OpenAPI JSON http://localhost:8080/v3/api-docs
 *
 * 우측 상단 Authorize 버튼으로 두 가지를 넣을 수 있다.
 *   bearerAuth   로그인 응답의 accessToken (Bearer 접두 없이 토큰만 붙여넣는다)
 *   adminKey     관리자 API 용 X-Admin-Key
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String ADMIN_KEY = "adminKey";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MCM 케어 API")
                        .version("v1")
                        .description("""
                                명품 AS 접수 · 견적 · 픽업 서비스

                                **테스트 계정**
                                - 고객 `user@example.com` / `Password123!`
                                - 기사 `driver01` / `Driver123!`

                                **사용 순서**
                                1. `POST /api/member/login` 으로 accessToken 발급
                                2. 우측 상단 Authorize → bearerAuth 에 토큰 입력
                                3. 나머지 API 호출

                                관리자 API 는 adminKey 에 `X-Admin-Key` 값을 입력한다.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답의 accessToken"))
                        .addSecuritySchemes(ADMIN_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Key")
                                .description("관리자 API 전용 헤더")));
    }
}
