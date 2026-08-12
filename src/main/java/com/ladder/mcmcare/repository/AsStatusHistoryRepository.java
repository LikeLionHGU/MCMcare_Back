package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.AsStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsStatusHistoryRepository extends JpaRepository<AsStatusHistory, Long> {

    List<AsStatusHistory> findByAsCaseIdOrderByOccurredAt(Long asId);
}
