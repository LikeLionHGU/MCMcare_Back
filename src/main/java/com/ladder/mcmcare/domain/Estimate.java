package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 견적 결과.
 *
 * 저장하지 않으면 조회할 때마다 AI 를 다시 호출해야 한다.
 * 그러면 같은 접수 건인데 열어볼 때마다 금액이 달라지고, 조회에 수십 초가 걸린다.
 *
 * 접수 1건당 1개이며, 재분석하면 덮어쓴다.
 * 이전 견적을 보여줄 화면이 없어 이력을 남기지 않는다.
 *
 * 보증 판정(warrantyVerdict)은 저장하지 않는다 —
 * 구매일과 보증기간만으로 결정되므로 조회 시점에 계산해도 결과가 같다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "estimate",
        uniqueConstraints = @UniqueConstraint(name = "uk_estimate_as", columnNames = "as_id")
)
public class Estimate extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "estimate_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "as_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_estimate_as"))
    private AsCase asCase;

    /** 분류된 손상 유형. 여러 건이면 " · " 로 이어 붙인 값 */
    @Column(name = "damage_category", length = 200)
    private String damageCategory;

    /** "중간 — 부분 수선 가능 수준" */
    @Column(name = "damage_severity", length = 50)
    private String damageSeverity;

    /** 높음 / 보통 / 낮음 */
    @Column(name = "confidence_grade", length = 20)
    private String confidenceGrade;

    /** "제출 사진 3장 기반" */
    /** "제출 사진 2장 기반 · 가방 전체가 사진에 담기지 않아 …" 형태. 경고가 붙으면 길어진다 */
    @Column(name = "confidence_note", length = 200)
    private String confidenceNote;

    /** 손상을 탐지하지 못한 경우의 안내. 값이 있으면 itemList 는 비어 있다. */
    @Column(name = "no_damage_notice", length = 300)
    private String noDamageNotice;

    /**
     * AI 서버 응답 원문.
     * 화면에는 쓰지 않는다. 표시된 금액이 이상할 때 원인을 추적하는 용도다.
     */
    @Lob
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<EstimateItem> items = new ArrayList<>();

    @Builder
    private Estimate(AsCase asCase, String damageCategory, String damageSeverity,
                     String confidenceGrade, String confidenceNote,
                     String noDamageNotice, String rawResponse) {
        this.asCase = asCase;
        this.damageCategory = damageCategory;
        this.damageSeverity = damageSeverity;
        this.confidenceGrade = confidenceGrade;
        this.confidenceNote = confidenceNote;
        this.noDamageNotice = noDamageNotice;
        this.rawResponse = rawResponse;
    }

    public void addItem(EstimateItem item) {
        this.items.add(item);
    }
}
