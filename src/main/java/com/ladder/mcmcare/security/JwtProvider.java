package com.ladder.mcmcare.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expireMillis;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expire-minutes}") long expireMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMinutes * 60 * 1000;
    }

    public String createToken(Long id, String username, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 초 단위 만료 시간. 로그인 응답의 expiresIn */
    public long getExpiresInSeconds() {
        return expireMillis / 1000;
    }

    /**
     * @return 검증된 principal. 서명 오류·형식 오류면 null.
     * @throws ExpiredJwtException 만료된 토큰
     */
    public PrincipalDetails parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return PrincipalDetails.builder()
                    .id(Long.valueOf(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .role(Role.valueOf(claims.get("role", String.class)))
                    .build();

        } catch (ExpiredJwtException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
}
