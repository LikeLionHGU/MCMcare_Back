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

    /** HS256 최소 키 길이 (RFC 7518) */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expire-minutes}") long expireMinutes) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // 그냥 두면 jjwt 가 WeakKeyException 을 던지는데 원인이 드러나지 않는다
            throw new IllegalStateException(
                    "app.jwt.secret 이 너무 짧습니다 (%d bytes). HS256 은 최소 %d bytes 가 필요합니다."
                            .formatted(bytes.length, MIN_SECRET_BYTES));
        }

        this.key = Keys.hmacShaKeyFor(bytes);
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
