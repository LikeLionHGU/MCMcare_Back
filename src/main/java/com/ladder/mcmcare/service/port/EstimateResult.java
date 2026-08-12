package com.ladder.mcmcare.service.port;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 견적 분석 결과.
 *
 * 712 화면이 표시해야 하는 값들이 곧 이 계약이다.
 * AI 출력 형태가 어떻게 정해지든 최종적으로 이 형태로 매핑된다.
 */
@Getter
@Builder
public class EstimateResult {

    /** 분류된 손상 유형 — "하드웨어 마모 및 가죽 스티칭 분리" */
    private String damageCategory;

    /** 손상 정도 — "중간 — 부분 수선 가능 수준" */
    private String damageSeverity;

    /** 분석 신뢰도 등급 — 높음 / 보통 / 낮음 */
    private String confidenceGrade;

    /** "제출 사진 2장 기반" */
    private String confidenceNote;

    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private String repairItemName;
        private int minPrice;
        private int maxPrice;
    }

    public int totalMinPrice() {
        return items.stream().mapToInt(Item::getMinPrice).sum();
    }

    public int totalMaxPrice() {
        return items.stream().mapToInt(Item::getMaxPrice).sum();
    }
}
