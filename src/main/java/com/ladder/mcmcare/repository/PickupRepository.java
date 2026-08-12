package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    Optional<Pickup> findByPickupNo(String pickupNo);

    boolean existsByPickupNo(String pickupNo);

    long countByPickupNoStartingWith(String prefix);

    /** 716 상세의 pickupNo — BOOKED 또는 COMPLETED 중 최근 1건 */
    Optional<Pickup> findFirstByAsCaseIdAndStatusInOrderByCreatedAtDesc(
            Long asId, Collection<PickupStatus> statuses);

    boolean existsByAsCaseIdAndStatus(Long asId, PickupStatus status);

    /** 슬롯 가용성 판정 */
    long countByPickupDateAndSlotStartAndStatus(
            LocalDate pickupDate, LocalTime slotStart, PickupStatus status);

    /** 슬롯 조회 범위 내 예약 건수 일괄 조회 */
    List<Pickup> findByPickupDateBetweenAndStatus(
            LocalDate from, LocalDate to, PickupStatus status);

    /** 기사 배차 목록 */
    List<Pickup> findByDriverIdAndPickupDateAndStatusOrderBySlotStart(
            Long driverId, LocalDate pickupDate, PickupStatus status);

    /** 자동 인계 스케줄러 대상 */
    List<Pickup> findByStatusAndPickupDateLessThanEqual(PickupStatus status, LocalDate date);
}
