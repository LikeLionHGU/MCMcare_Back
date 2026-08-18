package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.AsPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AsPhotoRepository extends JpaRepository<AsPhoto, Long> {

    List<AsPhoto> findByAsCaseIdOrderBySortOrder(Long asId);

    /**
     * 목록 화면 썸네일용 — 여러 접수 건의 사진을 한 번에 가져온다.
     * 건마다 조회하면 목록 크기만큼 쿼리가 나간다(N+1).
     */
    List<AsPhoto> findByAsCaseIdInOrderByAsCaseIdAscSortOrderAsc(Collection<Long> asIds);
}
