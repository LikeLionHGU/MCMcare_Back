package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.HandoverPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverPhotoRepository extends JpaRepository<HandoverPhoto, Long> {

    List<HandoverPhoto> findByHandoverIdOrderBySortOrder(Long handoverId);
}
