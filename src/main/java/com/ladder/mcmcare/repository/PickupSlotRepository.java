package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.PickupSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, Long> {

    List<PickupSlot> findBySlotDateBetweenOrderBySlotDateAscSlotStartAsc(LocalDate from, LocalDate to);

    /**
     * 예약 생성 시 슬롯 행을 잠근다.
     * COUNT 후 INSERT 는 MySQL 기본 격리수준에서 팬텀을 막지 못하므로,
     * 두 요청이 동시에 정원 미달로 판정할 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PickupSlot s where s.slotDate = :date and s.slotStart = :start")
    Optional<PickupSlot> findForUpdate(@Param("date") LocalDate date, @Param("start") LocalTime start);
}
