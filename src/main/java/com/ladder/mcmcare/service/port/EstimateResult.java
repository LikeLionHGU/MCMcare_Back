package com.ladder.mcmcare.service.port;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI 견적 분석 결과.
 *
 * 712 화면이 표시해야 하는 값들이 곧 이 계약이다.
 * AI 출력 형태가 어떻게 바뀌든 최종적으로 이 형태로 매핑된다.
 *
 * 금액은 모두 원화(KRW)다. AI 서버는 EUR 로 응답하므로 어댑터가 환산해서 채운다.
 */
@Getter
@Builder
public class EstimateResult {

    /** 분류된 손상 유형 — AI 카테고리 그대로 (예: "찢김/파열") */
    private String damageCategory;

    /** 손상 정도 — "중간 — 부분 수선 가능 수준" */
    private String damageSeverity;

    /** 분석 신뢰도 등급 — 높음 / 보통 / 낮음 */
    private String confidenceGrade;

    /** "제출 사진 2장 기반" */
    private String confidenceNote;

    /** 손상을 하나도 찾지 못한 경우 빈 리스트 */
    private List<Item> items;

    /**
     * AI 가 손상을 탐지하지 못했을 때 화면에 노출할 안내.
     * 이 값이 있으면 비용 영역 대신 안내 문구를 표시한다.
     */
    private String noDamageNotice;

    /**
     * AI 서버 응답 원문. 화면에는 쓰지 않고 저장만 한다.
     * 표시된 금액이 이상할 때 원인을 추적하는 용도다. 스텁은 null.
     */
    private String rawResponse;

    @Getter
    @Builder
    public static class Item {

        /** AI 카테고리명 (예: "찢김/파열") */
        private String repairItemName;

        /**
         * 이번 건의 추정 금액.
         * AI 의 point_estimate 를 환산한 값이며, 손상 면적비가 반영돼 있다.
         * min/max 는 카테고리 고정 구간이라 손상 정도가 드러나지 않으므로 이 값이 필요하다.
         */
        private int estimatedPrice;

        /** 카테고리 최소 구간 (손상 정도 무관) */
        private int minPrice;

        /** 카테고리 최대 구간 (손상 정도 무관) */
        private int maxPrice;
    }

    /** 손상 미탐지 여부 */
    public boolean isNoDamage() {
        return items == null || items.isEmpty();
    }

    /** 항목별 추정 금액 합계 */
    public int totalEstimatedPrice() {
        return isNoDamage() ? 0 : items.stream().mapToInt(Item::getEstimatedPrice).sum();
    }

    public int totalMinPrice() {
        return isNoDamage() ? 0 : items.stream().mapToInt(Item::getMinPrice).sum();
    }

    public int totalMaxPrice() {
        return isNoDamage() ? 0 : items.stream().mapToInt(Item::getMaxPrice).sum();
    }
}
