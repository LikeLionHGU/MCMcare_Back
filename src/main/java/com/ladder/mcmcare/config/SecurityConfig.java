package com.ladder.mcmcare.config;

import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.exception.ErrorResponse;
import com.ladder.mcmcare.security.AdminKeyFilter;
import com.ladder.mcmcare.security.JwtAuthorizationFilter;
import com.ladder.mcmcare.security.JwtProvider;
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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity          // @PreAuthorize 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Value("${app.admin-key}")
    private String adminKey;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/member/signup",
                                "/api/member/login",
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

                .addFilterBefore(new AdminKeyFilter(adminKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthorizationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
