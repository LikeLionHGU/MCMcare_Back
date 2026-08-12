package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.MarketingConsent;
import com.ladder.mcmcare.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketingConsentRepository extends JpaRepository<MarketingConsent, Long> {

    /** 현재 동의 여부 = 최신 행의 agreed */
    Optional<MarketingConsent> findFirstByMemberOrderByOccurredAtDesc(Member member);
}
