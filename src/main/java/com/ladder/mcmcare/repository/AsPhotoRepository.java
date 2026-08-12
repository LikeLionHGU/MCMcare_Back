package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.AsPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsPhotoRepository extends JpaRepository<AsPhoto, Long> {

    List<AsPhoto> findByAsCaseIdOrderBySortOrder(Long asId);
}
