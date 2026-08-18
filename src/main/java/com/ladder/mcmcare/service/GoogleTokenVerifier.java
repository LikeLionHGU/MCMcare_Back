package com.ladder.mcmcare.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 구글 ID 토큰을 검증하고 사용자 정보를 꺼낸다.
 *
 * 프론트가 구글 SDK 로 받은 ID 토큰을 그대로 서버에 보내면,
 * 서버는 구글 공개키(JWKS)로 서명을 확인한다. 클라이언트 시크릿은 필요 없다.
 *
 * 검증 항목
 *   서명       구글이 발급한 토큰인지
 *   aud        우리 클라이언트 ID 로 발급됐는지 (다른 앱 토큰 차단)
 *   iss        accounts.google.com 인지
 *   exp        만료되지 않았는지
 *   email_verified  구글이 이메일 소유를 확인했는지
 *
 * 이 중 하나라도 빠지면 위조 토큰으로 남의 계정에 로그인할 수 있다.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUser verify(String idToken) {

        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("구글 ID 토큰 검증 실패", e);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (token == null) {
            // 서명 · aud · iss · exp 중 하나라도 어긋나면 null 이 반환된다
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        GoogleIdToken.Payload payload = token.getPayload();

        // 구글이 소유를 확인하지 않은 이메일은 신뢰할 수 없다.
        // 그대로 받으면 타인 이메일로 계정을 만들거나 기존 계정에 붙을 수 있다.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            log.warn("이메일 미인증 구글 계정: {}", payload.getEmail());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Object name = payload.get("name");
        return new GoogleUser(email, name == null ? email.split("@")[0] : name.toString());
    }

    @Getter
    public static class GoogleUser {
        private final String email;
        private final String name;

        public GoogleUser(String email, String name) {
            this.email = email;
            this.name = name;
        }
    }
}
