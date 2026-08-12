package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 픽업 가능 슬롯 마스터.
 *
 * 시드가 없으면 픽업 날짜 선택이 불가능하다.
 * 슬롯 단위(30분 / 2시간)가 바뀌어도 행만 다시 깔면 되고 스키마·코드는 그대로다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "pickup_slot",
        uniqueConstraints = @UniqueConstraint(name = "uk_slot", columnNames = {"slot_date", "slot_start"})
)
public class PickupSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long id;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_start", nullable = false)
    private LocalTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private LocalTime slotEnd;

    /** 동시 수거 가능 건수 */
    @Column(nullable = false)
    private int capacity;

    /** 운영 차단 */
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Builder
    private PickupSlot(LocalDate slotDate, LocalTime slotStart, LocalTime slotEnd,
                       int capacity, boolean blocked) {
        this.slotDate = slotDate;
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.capacity = capacity;
        this.blocked = blocked;
    }
}
