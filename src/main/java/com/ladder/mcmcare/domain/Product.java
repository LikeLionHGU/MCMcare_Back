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
 * 소유자(member) 컬럼을 두지 않는다.
 * 브랜드가 구매자 정보를 제공하지 않고, 제공하더라도 그 구매자가 우리 회원이라는 보장이 없다.
 * 실제로 보증서는 제품에 딸려가는 증서이므로 소지자가 권리를 행사한다.
 * 채울 수 없는 컬럼을 두면 그에 기댄 검증이 항상 통과해 오히려 오해를 만든다.
 *
 * 구매일·구매처는 브랜드 보증 조회로 확인되지 않는 경우가 있어 nullable 이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product")
public class Product {

    /** 보증서 번호가 곧 PK */
    @Id
    @Column(name = "warranty_no", length = 50)
    private String warrantyNo;

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
    private Product(String warrantyNo, ProductType productType, String modelName,
                    LocalDate purchasedAt, PurchaseChannel purchaseChannel,
                    int warrantyMonths, String warrantyScope) {
        this.warrantyNo = warrantyNo;
        this.productType = productType;
        this.modelName = modelName;
        this.purchasedAt = purchasedAt;
        this.purchaseChannel = purchaseChannel;
        this.warrantyMonths = warrantyMonths;
        this.warrantyScope = warrantyScope;
        this.warrantyExpiresAt = (purchasedAt == null) ? null : purchasedAt.plusMonths(warrantyMonths);
    }
}
