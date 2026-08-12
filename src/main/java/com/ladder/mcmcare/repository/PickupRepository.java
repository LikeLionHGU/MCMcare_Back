package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 자동 인계 스케줄러 대상 — ID 만 반환한다.
     *
     * 엔티티를 그대로 반환하면 트랜잭션 밖으로 나가 detached 가 되어
     * LAZY 초기화가 실패하고 변경도 flush 되지 않는다.
     * 호출부는 이 ID 로 트랜잭션 안에서 다시 조회한다.
     */
    @Query("select p.id from Pickup p " +
           "where p.status = :status and p.pickupDate <= :date and p.driver is not null")
    List<Long> findIdsForAutoHandover(@Param("status") PickupStatus status,
                                      @Param("date") LocalDate date);
}
