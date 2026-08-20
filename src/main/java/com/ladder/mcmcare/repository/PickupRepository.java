package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    Optional<Pickup> findByPickupNo(String pickupNo);

    /**
     * 인계 처리용 — 픽업 행을 잠근다.
     * 고객 취소와 기사 인계가 동시에 들어오면 둘 다 BOOKED 를 읽고 진행할 수 있으므로
     * 상태를 바꾸는 명령은 행을 잠근 뒤 확인해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pickup p where p.pickupNo = :pickupNo")
    Optional<Pickup> findByPickupNoForUpdate(@Param("pickupNo") String pickupNo);

    /**
     * 자동 인계 스케줄러용 — 픽업 행을 잠근다.
     *
     * 스케줄러는 ID 목록을 먼저 뽑고 건별로 처리하는데, 그 사이에 고객이 취소할 수 있다.
     * 고객 취소(getOwnedForUpdate)와 기사 수동 인계(findByPickupNoForUpdate)는
     * 같은 행을 잠그므로, 자동 경로만 락이 없으면 취소된 건이 인계될 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pickup p where p.id = :pickupId")
    Optional<Pickup> findByIdForUpdate(@Param("pickupId") Long pickupId);

    boolean existsByPickupNo(String pickupNo);

    long countByPickupNoStartingWith(String prefix);

    /** 716 상세의 pickupNo — BOOKED 또는 COMPLETED 중 최근 1건 */
    Optional<Pickup> findFirstByAsCaseIdAndStatusInOrderByCreatedAtDesc(
            Long asId, Collection<PickupStatus> statuses);

    boolean existsByAsCaseIdAndStatus(Long asId, PickupStatus status);

    /** 상태와 무관하게 픽업이 하나라도 있는지. 방치 접수 정리에서 예약 흔적을 확인한다. */
    boolean existsByAsCaseId(Long asId);

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
           "where p.status = :status and p.pickupDate <= :date " +
           "and p.driver is not null and p.driver.active = true")
    List<Long> findIdsForAutoHandover(@Param("status") PickupStatus status,
                                      @Param("date") LocalDate date);

    /**
     * 인계 대기 중인데 담당 기사가 비활성이거나 미배정인 건.
     * 자동 인계 대상에서 빠지므로 운영자가 알아챌 수 있도록 로그로 남긴다.
     * 재배차 기능이 없는 상태에서 조용히 넘어가면 인계가 영원히 지연된다.
     */
    @Query("select p.pickupNo from Pickup p " +
           "where p.status = :status and p.pickupDate <= :date " +
           "and (p.driver is null or p.driver.active = false)")
    List<String> findStalledPickupNos(@Param("status") PickupStatus status,
                                      @Param("date") LocalDate date);
}
