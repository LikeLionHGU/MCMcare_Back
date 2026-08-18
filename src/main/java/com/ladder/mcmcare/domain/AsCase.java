package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AS 접수 — 서비스의 허브.
 *
 * 제품 정보를 자체 보유하는 이유:
 * 보증서 번호가 필수입력이 아니므로 product 에 존재하지 않는 제품이 접수될 수 있다.
 * 자동 채움 후 사용자가 값을 수정하면 이 스냅샷이 해당 접수 건의 진실이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "as_case",
        uniqueConstraints = @UniqueConstraint(name = "uk_as_no", columnNames = "as_no"),
        indexes = {
                @Index(name = "idx_as_member_status", columnList = "member_id, status"),
                @Index(name = "idx_as_member_created", columnList = "member_id, created_at DESC")
        }
)
public class AsCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "as_id")
    private Long id;

    /** 노출용 접수번호. 포맷 변경 가능성이 있으므로 FK 로 쓰지 않는다. */
    @Column(name = "as_no", nullable = false, length = 30)
    private String asNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_as_member"))
    private Member member;

    /** 필수입력 아님 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warranty_no", foreignKey = @ForeignKey(name = "fk_as_product"))
    private Product product;

    // ── 제품 스냅샷 ──
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

    // ── 손상 정보 ──
    @Column(name = "damage_part", nullable = false, length = 50)
    private String damagePart;

    @Enumerated(EnumType.STRING)
    @Column(name = "damage_type", nullable = false, length = 30)
    private DamageType damageType;

    @Column(name = "damage_description", length = 200)
    private String damageDescription;

    // ── 진행 상태 ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AsStatus status;

    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    @Column(name = "status_message", length = 200)
    private String statusMessage;

    @Column(name = "expected_completed_at")
    private LocalDate expectedCompletedAt;

    /** 예상 완료일이 마지막으로 조정된 날 */
    @Column(name = "expected_updated_at")
    private LocalDate expectedUpdatedAt;

    /** 실제 완료일. 목록에서 완료 건은 예상일 대신 이 값을 표시한다. */
    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(name = "delay_reason", length = 200)
    private String delayReason;

    @Column(name = "intake_type", length = 30)
    private String intakeType;

    // ── 위치 ──
    @Column(name = "current_location", length = 50)
    private String currentLocation;

    @Column(name = "location_type", length = 20)
    private String locationType;

    @Column(name = "location_status", length = 30)
    private String locationStatus;

    @OneToMany(mappedBy = "asCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<AsPhoto> photos = new ArrayList<>();

    @Builder
    private AsCase(String asNo, Member member, Product product,
                   ProductType productType, String modelName,
                   LocalDate purchasedAt, PurchaseChannel purchaseChannel,
                   String damagePart, DamageType damageType, String damageDescription) {
        this.asNo = asNo;
        this.member = member;
        this.product = product;
        this.productType = productType;
        this.modelName = modelName;
        this.purchasedAt = purchasedAt;
        this.purchaseChannel = purchaseChannel;
        this.damagePart = damagePart;
        this.damageType = damageType;
        this.damageDescription = damageDescription;
        this.status = AsStatus.DRAFT;
        this.statusUpdatedAt = LocalDateTime.now();
        this.intakeType = "픽업 수거 접수";
    }

    /** 접수번호 UNIQUE 충돌 시 재발급 (영속화 이전에만 호출) */
    public void reassignAsNo(String asNo) {
        this.asNo = asNo;
    }

    /**
     * 상태 전이. AsStatusService 를 통해서만 호출한다.
     * 직접 호출하면 as_status_history 와 어긋나 목록 뱃지와 상세 타임라인이 달라진다.
     */
    public void changeStatus(AsStatus next, String statusMessage) {
        this.status = next;
        this.statusUpdatedAt = LocalDateTime.now();
        if (statusMessage != null) {
            this.statusMessage = statusMessage;
        }
        if (next == AsStatus.COMPLETED) {
            this.completedAt = LocalDate.now();
        }
    }

    public void updateSchedule(LocalDate expectedCompletedAt, String delayReason) {
        if (expectedCompletedAt != null && !expectedCompletedAt.equals(this.expectedCompletedAt)) {
            this.expectedCompletedAt = expectedCompletedAt;
            this.expectedUpdatedAt = LocalDate.now();
        }
        if (delayReason != null) {
            this.delayReason = delayReason;
        }
    }

    public void updateLocation(String current, String type, String status) {
        if (current != null) this.currentLocation = current;
        if (type != null) this.locationType = type;
        if (status != null) this.locationStatus = status;
    }

    public void addPhoto(AsPhoto photo) {
        this.photos.add(photo);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.member.getId().equals(memberId);
    }
}
