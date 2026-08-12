package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 보증서 단위 구매 인스턴스.
 * 용도는 둘뿐이다 — 접수 폼 자동 채움, 보증 판정.
 *
 * 구매일·구매처는 브랜드 보증 조회로 확인되지 않는 경우가 있어 nullable 이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product",
        indexes = @Index(name = "idx_product_member", columnList = "member_id")
)
public class Product {

    /** 보증서 번호가 곧 PK */
    @Id
    @Column(name = "warranty_no", length = 50)
    private String warrantyNo;

    /** 소유자 확인 전이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", foreignKey = @ForeignKey(name = "fk_product_member"))
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(name = "model_name", nullable = false, length = 50)
    private String modelName;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_channel", length = 30)
    private PurchaseChannel purchaseChannel;

    /** 24 = 2년 */
    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    /** purchased_at + warranty_months 계산 캐시 */
    @Column(name = "warranty_expires_at")
    private LocalDate warrantyExpiresAt;

    /** "제조 결함 한정" 등 보증 범위 조건 */
    @Column(name = "warranty_scope", length = 50)
    private String warrantyScope;

    @Builder
    private Product(String warrantyNo, Member member, ProductType productType, String modelName,
                    LocalDate purchasedAt, PurchaseChannel purchaseChannel,
                    int warrantyMonths, String warrantyScope) {
        this.warrantyNo = warrantyNo;
        this.member = member;
        this.productType = productType;
        this.modelName = modelName;
        this.purchasedAt = purchasedAt;
        this.purchaseChannel = purchaseChannel;
        this.warrantyMonths = warrantyMonths;
        this.warrantyScope = warrantyScope;
        this.warrantyExpiresAt = (purchasedAt == null) ? null : purchasedAt.plusMonths(warrantyMonths);
    }
}
