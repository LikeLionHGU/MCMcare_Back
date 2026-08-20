package com.ladder.mcmcare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 개발용 기본 비밀값이 운영에 그대로 나가는 것을 막는다.
 *
 * prod 프로필에서 기본값이 쓰이고 있으면 기동을 중단시킨다.
 * yaml 에 커밋된 값은 어차피 GitHub 에 공개되므로, 그대로 배포되면
 * JWT 위조로 임의 사용자 인증이 가능해진다.
 *
 * prod 가 아닌 프로필에서는 경고만 남긴다.
 * "local 이 아니면 막는다"로 두면 dev · demo 같은 임의 프로필로 띄울 때
 * 기동 자체가 막혀 시연 중 사고가 된다.
 *
 * ⚠️ 감시 대상을 추가할 때는 SECRETS 맵에만 넣으면 된다.
 *    실패 메시지가 환경변수 이름을 그대로 안내하므로 문서와 어긋날 일이 없다.
 */
@Slf4j
@Component
public class SecretsCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final String PROD_PROFILE = "prod";

    /** 환경변수 이름 → 개발용 기본값 */
    private static final Map<String, String> DEFAULTS = Map.of(
            "JWT_SECRET",       "local-dev-only-secret-key-not-for-production-32b",
            "ADMIN_KEY",        "local-dev-only-admin-key",
            "FILE_SIGN_SECRET", "local-dev-only-file-sign-secret-key-32b",
            "DB_PASSWORD",      "local-dev-only-db-password"
    );

    private final Environment env;
    private final Map<String, String> current;

    public SecretsCheck(Environment env,
                        @Value("${app.jwt.secret}") String jwtSecret,
                        @Value("${app.admin-key}") String adminKey,
                        @Value("${app.file.sign-secret}") String fileSignSecret,
                        @Value("${spring.datasource.password}") String dbPassword) {
        this.env = env;
        this.current = Map.of(
                "JWT_SECRET", jwtSecret,
                "ADMIN_KEY", adminKey,
                "FILE_SIGN_SECRET", fileSignSecret,
                "DB_PASSWORD", dbPassword
        );
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        boolean strict = List.of(env.getActiveProfiles()).contains(PROD_PROFILE);

        List<String> leaked = new ArrayList<>();
        DEFAULTS.forEach((name, defaultValue) -> {
            if (defaultValue.equals(current.get(name))) leaked.add(name);
        });

        if (leaked.isEmpty()) return;
        leaked.sort(String::compareTo);

        if (!strict) {
            log.warn("""

                    ⚠️  개발용 기본 비밀값을 사용 중입니다: {}
                       배포 전 환경변수로 교체하세요.
                    """, leaked);
        } else {
            throw new IllegalStateException(
                    "운영 환경에서 개발용 기본 비밀값이 감지되었습니다: " + leaked
                            + " — 해당 환경변수를 주입한 뒤 다시 기동하세요.");
        }
    }
}
