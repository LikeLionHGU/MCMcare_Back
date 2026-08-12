package com.ladder.mcmcare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 기본 비밀값이 운영에 그대로 나가는 것을 막는다.
 *
 * local 프로필이 아닌데 기본값이 쓰이고 있으면 기동을 중단시킨다.
 * yaml 에 커밋된 값은 어차피 GitHub 에 공개되므로, 그대로 배포되면
 * JWT 위조로 임의 사용자 인증이 가능해진다.
 */
@Slf4j
@Component
public class SecretsCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final String DEFAULT_JWT = "local-dev-only-secret-key-not-for-production-32b";
    private static final String DEFAULT_ADMIN = "local-dev-only-admin-key";

    private final Environment env;
    private final String jwtSecret;
    private final String adminKey;

    public SecretsCheck(Environment env,
                        @Value("${app.jwt.secret}") String jwtSecret,
                        @Value("${app.admin-key}") String adminKey) {
        this.env = env;
        this.jwtSecret = jwtSecret;
        this.adminKey = adminKey;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        boolean local = List.of(env.getActiveProfiles()).contains("local")
                || env.getActiveProfiles().length == 0;

        List<String> leaked = new ArrayList<>();
        if (DEFAULT_JWT.equals(jwtSecret)) leaked.add("JWT_SECRET");
        if (DEFAULT_ADMIN.equals(adminKey)) leaked.add("ADMIN_KEY");

        if (leaked.isEmpty()) return;

        if (local) {
            log.warn("""

                    ⚠️  개발용 기본 비밀값을 사용 중입니다: {}
                       배포 전 환경변수로 교체하세요.
                    """, leaked);
        } else {
            throw new IllegalStateException(
                    "운영 환경에서 개발용 기본 비밀값이 감지되었습니다: " + leaked
                            + " — 환경변수를 주입한 뒤 다시 기동하세요.");
        }
    }
}
