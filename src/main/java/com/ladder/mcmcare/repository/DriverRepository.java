package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByLoginId(String loginId);

    /** 배차 — active 기사 중 첫 번째 */
    Optional<Driver> findFirstByActiveTrueOrderByIdAsc();
}
