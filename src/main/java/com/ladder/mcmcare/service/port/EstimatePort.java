package com.ladder.mcmcare.service.port;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;

import java.util.List;

/**
 * AI 견적 분석.
 *
 * 구현체가 둘 이상 공존하므로 인터페이스로 분리한다.
 *   StubEstimateAdapter   규칙 기반 (기본)
 *   ModelEstimateAdapter  실제 모델 연동 (@Profile("ai"))
 *
 * 호출은 반드시 트랜잭션 밖에서 이루어져야 한다.
 * 수 초~수십 초가 걸리는 외부 I/O 이므로 트랜잭션 안에 두면 커넥션 풀이 고갈된다.
 */
public interface EstimatePort {

    EstimateResult analyze(AsCase asCase, List<AsPhoto> photos);
}
