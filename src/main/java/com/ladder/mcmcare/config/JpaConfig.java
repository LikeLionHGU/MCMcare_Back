package com.ladder.mcmcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableJpaAuditing      // created_at / updated_at 자동 관리
@EnableScheduling       // 자동 인계 스케줄러
public class JpaConfig {
}
