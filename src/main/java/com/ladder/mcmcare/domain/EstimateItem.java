package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 견적 항목별 금액.
 *
 * estimatedPrice 는 손상 면적비가 반영된 이번 건의 추정 금액이고,
 * minPrice · maxPrice 는 손상 유형별 고정 구간이다.
 * 둘 다 저장해야 화면이 "약 N원 (범위)" 형태를 그릴 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "estimate_item",
        indexes = @Index(name = "idx_estimate_item", columnList = "estimate_id, sort_order")
)
public class EstimateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_estimate"))
    private Estimate estimate;

    /** AI 카테고리명 (예: "찢김/파열") */
    @Column(name = "repair_item_name", nullable = false, length = 50)
    private String repairItemName;

    /** 손상 정도가 반영된 추정 금액 (원) */
    @Column(name = "estimated_price", nullable = false)
    private int estimatedPrice;

    @Column(name = "min_price", nullable = false)
    private int minPrice;

    @Column(name = "max_price", nullable = false)
    private int maxPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private EstimateItem(Estimate estimate, String repairItemName,
                         int estimatedPrice, int minPrice, int maxPrice, int sortOrder) {
        this.estimate = estimate;
        this.repairItemName = repairItemName;
        this.estimatedPrice = estimatedPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.sortOrder = sortOrder;
    }

    public static EstimateItem of(Estimate estimate, String repairItemName,
                                  int estimatedPrice, int minPrice, int maxPrice, int sortOrder) {
        return new EstimateItem(estimate, repairItemName, estimatedPrice, minPrice, maxPrice, sortOrder);
    }
}
