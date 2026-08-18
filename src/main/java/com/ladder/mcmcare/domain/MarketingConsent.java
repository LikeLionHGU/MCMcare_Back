package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 마케팅 수신 동의 이력.
 * 정보통신망법상 동의·철회 시점 기록과 증명 의무가 있으므로 INSERT 만 수행한다.
 * 현재 동의 여부는 최신 행의 agreed 값이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "marketing_consent",
        indexes = @Index(name = "idx_consent_member", columnList = "member_id, occurred_at DESC")
)
public class MarketingConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_consent_member"))
    private Member member;

    /** true 동의 / false 철회 */
    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private MarketingConsent(Member member, boolean agreed) {
        this.member = member;
        this.agreed = agreed;
        this.occurredAt = LocalDateTime.now();
    }

    public static MarketingConsent of(Member member, boolean agreed) {
        return new MarketingConsent(member, agreed);
    }
}
