package com.ladder.mcmcare.service.port;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;

import java.util.List;

/**
 * AI 견적 분석.
 *
 * 구현체가 둘 이상 공존하므로 인터페이스로 분리한다.
 * 어느 쪽이 뜰지는 app.estimate.provider 설정 하나로 결정된다.
 *
 *   provider: stub    StubEstimateAdapter   규칙 기반 (기본값)
 *   provider: model   ModelEstimateAdapter  AI 서버 연동
 *
 * 호출은 반드시 트랜잭션 밖에서 이루어져야 한다.
 * 수 초~수십 초가 걸리는 외부 I/O 이므로 트랜잭션 안에 두면 커넥션 풀이 고갈된다.
 */
public interface EstimatePort {

    EstimateResult analyze(AsCase asCase, List<AsPhoto> photos);
}
