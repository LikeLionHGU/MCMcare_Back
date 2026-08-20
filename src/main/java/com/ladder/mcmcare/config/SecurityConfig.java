package com.ladder.mcmcare.config;

import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.exception.ErrorResponse;
import com.ladder.mcmcare.repository.MemberRepository;
import com.ladder.mcmcare.security.AdminKeyFilter;
import com.ladder.mcmcare.security.JwtAuthorizationFilter;
import com.ladder.mcmcare.security.JwtProvider;
import com.ladder.mcmcare.security.SignedFileFilter;
import com.ladder.mcmcare.service.FileUrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity          // @PreAuthorize 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final FileUrlSigner fileUrlSigner;
    private final MemberRepository memberRepository;

    @Value("${app.admin-key}")
    private String adminKey;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/files/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Security 필터체인이 MVC 보다 먼저 돈다.
                // 여기서 CORS 를 연결하지 않으면 OPTIONS 프리플라이트가 authenticated() 에 걸려
                // 401 로 막히고, 브라우저에서 PUT/DELETE/JSON POST 가 전부 실패한다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/member/signup",
                                "/api/member/login",
                                "/api/member/login/google",
                                "/api/driver/login",
                                "/files/**"
                        ).permitAll()
                        // Swagger UI — 배포 시에는 차단하거나 springdoc.api-docs.enabled=false 로 끈다
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").permitAll()   // AdminKeyFilter 가 검사
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(ErrorCode.NO_PERMISSION.getStatus().value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            res.getWriter().write(jsonMapper.writeValueAsString(
                                    ErrorResponse.of(ErrorCode.NO_PERMISSION)));
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(ErrorCode.NO_PERMISSION.getStatus().value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            res.getWriter().write(jsonMapper.writeValueAsString(
                                    ErrorResponse.of(ErrorCode.NO_PERMISSION)));
                        })
                )

                .addFilterBefore(new SignedFileFilter(fileUrlSigner), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AdminKeyFilter(adminKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthorizationFilter(memberRepository, jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
