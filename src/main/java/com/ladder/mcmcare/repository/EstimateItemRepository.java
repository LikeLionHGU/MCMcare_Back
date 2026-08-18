package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.EstimateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstimateItemRepository extends JpaRepository<EstimateItem, Long> {

    List<EstimateItem> findByEstimateIdOrderBySortOrder(Long estimateId);

    /** 재분석 시 이전 항목을 지운다. estimate 보다 먼저 지워야 FK 위반이 나지 않는다. */
    void deleteByEstimateId(Long estimateId);
}
