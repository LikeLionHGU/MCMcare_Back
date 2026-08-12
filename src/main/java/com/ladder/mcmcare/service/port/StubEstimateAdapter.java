package com.ladder.mcmcare.service.port;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;
import com.ladder.mcmcare.domain.DamageType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 규칙 기반 견적 스텁.
 *
 * AI 모델이 완성되기 전까지 플로우를 살려두기 위한 구현체다.
 * 사용자가 선택한 손상 유형에 대응하는 수선 항목과 단가를 반환한다.
 *
 * 실제 모델이 붙어도 보증 판정 로직은 그대로 재사용된다 —
 * AI 는 부위 감지만 하고 가격·보증 판정은 서버가 담당하기 때문이다.
 *
 * ⚠️ 아래 단가는 임시값이다. 기획에서 확정된 단가표를 받으면 교체해야 한다.
 */
@Component
@Profile("!ai")
public class StubEstimateAdapter implements EstimatePort {

    /** 손상 유형 → (수선 항목명, 최소가, 최대가) */
    private static final Map<DamageType, Item> RULES = Map.of(
            DamageType.METAL_PART, new Item("하드웨어 교체",  80_000, 120_000),
            DamageType.STITCHING,  new Item("스티칭 수선",   40_000,  70_000),
            DamageType.DENT,       new Item("가죽 보수",     50_000,  90_000),
            DamageType.SCRATCH,    new Item("가죽 보수",     50_000,  90_000),
            DamageType.DISCOLOR,   new Item("색상 복원",     60_000, 110_000),
            DamageType.ETC,        new Item("기타 수선",     30_000, 150_000)
    );

    private record Item(String name, int min, int max) {}

    @Override
    public EstimateResult analyze(AsCase asCase, List<AsPhoto> photos) {

        Item rule = RULES.getOrDefault(asCase.getDamageType(), RULES.get(DamageType.ETC));

        return EstimateResult.builder()
                .damageCategory(asCase.getDamageType().getLabel())
                .damageSeverity("중간 — 부분 수선 가능 수준")
                .confidenceGrade(photos.size() >= 2 ? "높음" : "보통")
                .confidenceNote("제출 사진 %d장 기반".formatted(photos.size()))
                .items(List.of(EstimateResult.Item.builder()
                        .repairItemName(rule.name())
                        .minPrice(rule.min())
                        .maxPrice(rule.max())
                        .build()))
                .build();
    }
}
