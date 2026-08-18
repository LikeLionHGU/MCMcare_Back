package com.ladder.mcmcare.config;

import com.ladder.mcmcare.service.port.EstimatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 어떤 견적 구현체가 활성화됐는지 기동 시 로그로 남긴다.
 *
 * 프로필 조합(prod,ai) 대신 app.estimate.provider 로 결정하도록 바꿨지만,
 * 배포에서 실수로 stub 이 도는 상황을 눈으로 확인할 수 있어야 한다.
 * 스텁이 도는 줄 모르고 "AI 견적"이라고 안내하는 것이 가장 위험하다.
 */
@Slf4j
@Component
public class EstimateProviderCheck implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment env;
    private final EstimatePort estimatePort;
    private final String provider;

    public EstimateProviderCheck(Environment env,
                                 EstimatePort estimatePort,
                                 @Value("${app.estimate.provider}") String provider) {
        this.env = env;
        this.estimatePort = estimatePort;
        this.provider = provider;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        String impl = estimatePort.getClass().getSimpleName();
        boolean stub = "stub".equalsIgnoreCase(provider);
        boolean local = List.of(env.getActiveProfiles()).contains("local")
                || env.getActiveProfiles().length == 0;

        if (!stub) {
            log.info("견적 산출: AI 서버 연동 ({})", impl);
            return;
        }

        if (local) {
            log.info("견적 산출: 규칙 기반 스텁 ({}) — 실제 AI 를 쓰려면 app.estimate.provider=model", impl);
        } else {
            log.warn("""

                    ⚠️  배포 환경인데 견적이 규칙 기반 스텁({})으로 동작합니다.
                       화면에 표시되는 금액은 AI 분석 결과가 아닙니다.
                       실제 AI 를 사용하려면 app.estimate.provider=model 로 설정하세요.
                    """, impl);
        }
    }
}
