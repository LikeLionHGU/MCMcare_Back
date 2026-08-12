package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 픽업 예약.
 *
 * pickup_slot 과 FK 로 묶지 않는 이유:
 * 슬롯 정의가 바뀌면(30분 ↔ 2시간) 마스터 행을 갈아엎어야 하는데
 * FK 가 걸려 있으면 과거 예약이 함께 깨진다.
 * 시간 값을 자체 보유하고 가용성 판정만 (slot_date, slot_start) 로 논리 조인한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "pickup",
        uniqueConstraints = @UniqueConstraint(name = "uk_pickup_no", columnNames = "pickup_no"),
        indexes = {
                @Index(name = "idx_pickup_slot", columnList = "pickup_date, slot_start"),
                @Index(name = "idx_pickup_as", columnList = "as_id, status"),
                @Index(name = "idx_pickup_driver", columnList = "driver_id, pickup_date, status")
        }
)
public class Pickup extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pickup_id")
    private Long id;

    /** PKP-YYYY-NNNNNN */
    @Column(name = "pickup_no", nullable = false, length = 30)
    private String pickupNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "as_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pickup_as"))
    private AsCase asCase;

    /** 예약 확정 시 자동 배정 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", foreignKey = @ForeignKey(name = "fk_pickup_driver"))
    private Driver driver;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalTime slotEnd;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(nullable = false, length = 100)
    private String address;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    @Column(length = 200)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PickupStatus status;

    @Column(name = "insurance_applied")
    private Boolean insuranceApplied;

    /** 최대 500만원 */
    @Column(name = "insurance_limit")
    private Integer insuranceLimit;

    @Builder
    private Pickup(String pickupNo, AsCase asCase, Driver driver,
                   LocalDate pickupDate, LocalTime slotStart, LocalTime slotEnd,
                   String phone, String address, String addressDetail, String note,
                   Boolean insuranceApplied, Integer insuranceLimit) {
        this.pickupNo = pickupNo;
        this.asCase = asCase;
        this.driver = driver;
        this.pickupDate = pickupDate;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.note = note;
        this.insuranceApplied = insuranceApplied;
        this.insuranceLimit = insuranceLimit;
        this.status = PickupStatus.BOOKED;
    }

    /** 예약번호 UNIQUE 충돌 시 재발급 (영속화 이전에만 호출) */
    public void reassignPickupNo(String pickupNo) {
        this.pickupNo = pickupNo;
    }

    public void cancel() {
        this.status = PickupStatus.CANCELLED;
    }

    public void complete() {
        this.status = PickupStatus.COMPLETED;
    }

    public boolean isAssignedTo(Long driverId) {
        return this.driver != null && this.driver.getId().equals(driverId);
    }
}
