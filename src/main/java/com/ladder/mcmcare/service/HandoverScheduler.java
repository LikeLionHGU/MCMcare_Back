package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.PickupStatus;
import com.ladder.mcmcare.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 자동 인계 스케줄러 — 기사 앱 미배포 구간 대체.
 *
 * HandoverService.completeById() 를 호출하므로 자동·수동 어느 경로든 결과가 동일하다.
 *
 * ⚠️ 엔티티가 아닌 ID 목록을 조회한다.
 * 스케줄러 메서드에는 트랜잭션이 없어, 엔티티를 그대로 들고 나가면 detached 가 되어
 * LAZY 초기화(getDriver 등)가 실패하고 상태 변경도 DB 에 반영되지 않는다.
 *
 * 시간대는 보지 않고 날짜만 판정한다 — 당일 예약이 즉시 처리되도록 하기 위함이다.
 *
 * 실행 주기는 app.demo.handover-interval-ms 로 조정한다.
 * 시연 중 대기 시간을 줄이려면 짧게 잡으면 되고, 대상이 없으면 조회 한 번으로 끝나므로
 * 짧은 주기여도 부하는 사실상 없다.
 *
 * app.demo.auto-handover 를 false 로 두면 동작하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoverScheduler {

    private final PickupRepository pickupRepository;
    private final HandoverService handoverService;
    private final AsCaseService asCaseService;

    @Value("${app.demo.auto-handover}")
    private boolean enabled;

    @Value("${app.demo.handover-photo-urls}")
    private List<String> photoUrls;

    @Value("${app.demo.customer-sign-url}")
    private String customerSignUrl;

    @Value("${app.demo.driver-sign-url}")
    private String driverSignUrl;

    /** 이 시간을 넘겨 DRAFT · ANALYZING 으로 남아 있으면 실패 처리한다 */
    @Value("${app.estimate.stale-draft-minutes}")
    private int staleDraftMinutes;

    /**
     * AI 분석 도중 중단되어 남은 접수 건을 실패 처리한다.
     * 자동 인계와 주기를 공유하되, 임계 시간을 넘긴 건만 대상으로 한다.
     */
    @Scheduled(fixedDelayString = "${app.demo.handover-interval-ms}")
    public void recoverStaleDrafts() {
        int recovered = asCaseService.recoverStaleDrafts(staleDraftMinutes);
        if (recovered > 0) {
            log.warn("분석 미완료 접수 {}건을 실패 처리했습니다", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${app.demo.handover-interval-ms}")
    public void autoHandover() {

        if (!enabled) return;

        LocalDate today = LocalDate.now();

        // 담당 기사가 비활성이거나 미배정이면 자동 인계 대상에서 빠진다.
        // 재배차 기능이 없으므로 조용히 넘어가면 인계가 영원히 지연된다.
        List<String> stalled = pickupRepository.findStalledPickupNos(PickupStatus.BOOKED, today);
        if (!stalled.isEmpty()) {
            log.warn("자동 인계 불가 — 담당 기사 미배정 또는 비활성: {}", stalled);
        }

        List<Long> targetIds = pickupRepository.findIdsForAutoHandover(PickupStatus.BOOKED, today);

        for (Long pickupId : targetIds) {
            try {
                handoverService.completeById(pickupId, photoUrls, customerSignUrl, driverSignUrl);
                log.info("자동 인계 완료 pickupId={}", pickupId);
            } catch (Exception e) {
                log.warn("자동 인계 실패 pickupId={} : {}", pickupId, e.getMessage());
            }
        }
    }
}
