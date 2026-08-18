package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    Optional<Estimate> findByAsCaseId(Long asId);

    /** 재분석 시 이전 견적을 지운다. 이력을 남기지 않는다. */
    void deleteByAsCaseId(Long asId);
}
