package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Handover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    Optional<Handover> findByPickupId(Long pickupId);

    boolean existsByPickupId(Long pickupId);
}
