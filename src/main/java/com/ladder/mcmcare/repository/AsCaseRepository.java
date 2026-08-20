package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsCaseRepository extends JpaRepository<AsCase, Long> {

    Optional<AsCase> findByAsNo(String asNo);

    /**
     * 픽업 예약 생성용 — AS 행을 잠근다.
     *
     * 슬롯 락만으로는 부족하다. 서로 다른 슬롯으로 동시 요청이 오면
     * 슬롯 락이 겹치지 않아 "BOOKED 픽업이 이미 있는지" 검사를 둘 다 통과한다.
     * 결과적으로 한 접수 건에 유효한 픽업이 2건 생긴다.
     *
     * MySQL 은 부분 UNIQUE(WHERE status='BOOKED')를 지원하지 않아 DB 제약으로도 막을 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsCase a where a.asNo = :asNo")
    Optional<AsCase> findByAsNoForUpdate(@Param("asNo") String asNo);

    /**
     * AI 분석 흐름용 — AS 행을 잠근다.
     *
     * 분석 시작(ANALYZING 선점) · 결과 반영 · 실패 처리 · stale 복구가 모두 같은 행을 다툰다.
     * 여기에 고객 취소(findByAsNoForUpdate)까지 더해지므로 같은 락을 써야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsCase a where a.id = :asId")
    Optional<AsCase> findByIdForUpdate(@Param("asId") Long asId);

    boolean existsByAsNo(String asNo);

    /**
     * 홈 화면(상위 N)과 목록 화면(페이지)이 같은 조건을 쓴다.
     * 파라미터가 같으므로 메서드를 나눌 수 없다 — 반환 타입만 다른 오버로드는 컴파일되지 않는다.
     * 홈에서는 PageRequest.of(0, N) 으로 호출하고 getContent() 를 쓴다.
     */
    Page<AsCase> findByMemberIdAndStatusInOrderByCreatedAtDesc(
            Long memberId, Collection<AsStatus> statuses, Pageable pageable);

    long countByMemberIdAndStatusIn(Long memberId, Collection<AsStatus> statuses);

    /** 목록 상단 "최근 갱신" */
    @Query("select max(a.statusUpdatedAt) from AsCase a " +
           "where a.member.id = :memberId and a.status <> com.ladder.mcmcare.domain.AsStatus.CANCELLED")
    Optional<LocalDateTime> findLastUpdatedAt(@Param("memberId") Long memberId);

    /**
     * AI 분석 도중 서버가 중단되어 중간 상태로 남은 건.
     * 정상 흐름에서는 수 초 안에 ESTIMATED 또는 ESTIMATE_FAILED 로 바뀐다.
     */
    /**
     * 예약 없이 방치된 접수 건.
     *
     * 견적만 보고 나간 건이다. 목록에 노출되지 않아 사용자가 이어서 처리할 수 없으므로
     * 일정 시간이 지나면 취소해 정리한다.
     *
     * 픽업이 하나라도 걸려 있으면 제외한다 — 예약을 진행한 흔적이 있는 건이다.
     */
    @Query("""
            select a.id from AsCase a
            where a.status = com.ladder.mcmcare.domain.AsStatus.ESTIMATED
              and a.createdAt < :threshold
              and not exists (select 1 from Pickup p where p.asCase = a)
            """)
    List<Long> findAbandonedIds(@Param("threshold") LocalDateTime threshold);

    @Query("select a.id from AsCase a " +
           "where a.status in :statuses and a.createdAt < :threshold")
    List<Long> findStaleIds(@Param("statuses") Collection<AsStatus> statuses,
                            @Param("threshold") LocalDateTime threshold);

    /**
     * 접수번호 채번용 — 해당 연도 발급 건수.
     * JPQL 의 like 는 바인딩 파라미터에 % 를 직접 붙일 수 없으므로 파생 쿼리를 쓴다.
     */
    long countByAsNoStartingWith(String prefix);
}
