package com.ladder.mcmcare.service;

import com.ladder.mcmcare.repository.AsCaseRepository;
import com.ladder.mcmcare.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * 노출용 번호 채번.
 *
 * 포맷은 application.yaml 로 관리한다 — 스토리보드에 AS-YYYY-NNNNN 과 MCM-YYYY-NNNNNN 이
 * 혼재해 있어 확정되면 설정 한 줄만 바꾸면 된다.
 *
 * 동시 요청 시 같은 번호가 나올 수 있으므로 UNIQUE 제약 + 재시도로 방어한다.
 */
@Component
@RequiredArgsConstructor
public class NumberGenerator {

    private static final int MAX_RETRY = 5;

    private final AsCaseRepository asCaseRepository;
    private final PickupRepository pickupRepository;

    @Value("${app.number.as-format}")
    private String asFormat;

    @Value("${app.number.pickup-format}")
    private String pickupFormat;

    public String generateAsNo() {
        int year = Year.now().getValue();
        String prefix = asFormat.formatted(year, 0).replaceAll("\\d+$", "");
        long seq = asCaseRepository.countByAsNoStartingWith(prefix);

        for (int i = 0; i < MAX_RETRY; i++) {
            String candidate = asFormat.formatted(year, seq + 1 + i);
            if (!asCaseRepository.existsByAsNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("접수번호 채번에 실패했습니다.");
    }

    public String generatePickupNo() {
        int year = Year.now().getValue();
        String prefix = pickupFormat.formatted(year, 0).replaceAll("\\d+$", "");
        long seq = pickupRepository.countByPickupNoStartingWith(prefix);

        for (int i = 0; i < MAX_RETRY; i++) {
            String candidate = pickupFormat.formatted(year, seq + 1 + i);
            if (!pickupRepository.existsByPickupNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("픽업 예약번호 채번에 실패했습니다.");
    }
}
