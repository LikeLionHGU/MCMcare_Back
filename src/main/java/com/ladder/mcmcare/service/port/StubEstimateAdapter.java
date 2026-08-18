package com.ladder.mcmcare.service.port;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;
import com.ladder.mcmcare.domain.DamageType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 없이 동작하는 규칙 기반 스텁.
 *
 * AI 진단 서버(FastAPI)가 뜨지 않은 환경에서도 접수 → 견적 → 픽업 흐름을 확인할 수 있게 한다.
 * app.estimate.provider 가 stub 이거나 미설정이면 이 구현체가 활성화된다.
 *
 * 사용자가 고른 손상 유형을 AI 카테고리로 옮겨, 실제 모델과 같은 형태로 응답한다.
 * 비용은 AI 서버의 cost_mapping.py 구간(EUR)을 그대로 옮겨 왔다.
 */
@Component
@ConditionalOnProperty(name = "app.estimate.provider", havingValue = "stub", matchIfMissing = true)
@RequiredArgsConstructor
public class StubEstimateAdapter implements EstimatePort {

    /** 사용자 선택 손상 유형 → AI 카테고리 + 비용 구간(EUR) */
    private record Rule(String category, double minEur, double maxEur, double confidence) {}

    private static final Map<DamageType, Rule> RULES = Map.of(
            DamageType.STITCHING,  new Rule("찢김/파열",      100.0, 220.0, 0.72),
            DamageType.METAL_PART, new Rule("균열/파손",       80.0, 150.0, 0.68),
            DamageType.DENT,       new Rule("변형",           120.0, 200.0, 0.61),
            DamageType.SCRATCH,    new Rule("손상(세부미상)",  100.0, 200.0, 0.55),
            DamageType.DISCOLOR,   new Rule("손상(세부미상)",  100.0, 200.0, 0.52),
            DamageType.ETC,        new Rule("손상(세부미상)",  100.0, 200.0, 0.45)
    );

    private final EstimateMapper mapper;

    @Override
    public EstimateResult analyze(AsCase asCase, List<AsPhoto> photos) {

        Rule rule = RULES.getOrDefault(asCase.getDamageType(), RULES.get(DamageType.ETC));

        // 사진이 많을수록 신뢰도가 올라가는 것으로 가정한다 (실제 모델은 탐지 confidence 를 쓴다)
        double confidence = Math.min(0.95, rule.confidence() + 0.05 * (photos.size() - 1));

        String severity = photos.size() >= 2 ? "보통" : "경미";

        // 실제 모델은 손상 면적비로 point_estimate 를 보간한다.
        // 스텁에는 면적비가 없으므로 severity 등급을 대리 지표로 쓴다.
        double ratio = switch (severity) {
            case "심각" -> 0.60;
            case "보통" -> 0.30;
            default -> 0.10;
        };
        double pointEstimate = rule.minEur() + (rule.maxEur() - rule.minEur()) * ratio;

        return EstimateResult.builder()
                .damageCategory(rule.category())
                .damageSeverity(mapper.severityLabel(severity))
                .confidenceGrade(mapper.confidenceGrade(confidence))
                .confidenceNote(mapper.confidenceNote(photos.size()))
                .items(List.of(EstimateResult.Item.builder()
                        .repairItemName(rule.category())
                        .estimatedPrice(mapper.toKrwRound(pointEstimate))
                        .minPrice(mapper.toKrwFloor(rule.minEur()))
                        .maxPrice(mapper.toKrwCeil(rule.maxEur()))
                        .build()))
                .build();
    }
}
