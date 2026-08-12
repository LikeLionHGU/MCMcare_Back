package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsCaseRepository extends JpaRepository<AsCase, Long> {

    Optional<AsCase> findByAsNo(String asNo);

    boolean existsByAsNo(String asNo);

    /** 홈 화면 — 취소 건 제외, 최신순 상위 N */
    List<AsCase> findByMemberIdAndStatusNotOrderByCreatedAtDesc(
            Long memberId, AsStatus excluded, Pageable pageable);

    /** 목록 화면 — filter 에 해당하는 상태들만 */
    Page<AsCase> findByMemberIdAndStatusInOrderByCreatedAtDesc(
            Long memberId, Collection<AsStatus> statuses, Pageable pageable);

    long countByMemberIdAndStatusIn(Long memberId, Collection<AsStatus> statuses);

    /** 목록 상단 "최근 갱신" */
    @Query("select max(a.statusUpdatedAt) from AsCase a " +
           "where a.member.id = :memberId and a.status <> com.ladder.mcmcare.domain.AsStatus.CANCELLED")
    Optional<LocalDateTime> findLastUpdatedAt(@Param("memberId") Long memberId);

    /**
     * 접수번호 채번용 — 해당 연도 발급 건수.
     * JPQL 의 like 는 바인딩 파라미터에 % 를 직접 붙일 수 없으므로 파생 쿼리를 쓴다.
     */
    long countByAsNoStartingWith(String prefix);
}
