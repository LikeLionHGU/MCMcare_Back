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
 * app.demo.auto-handover 를 false 로 두면 동작하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoverScheduler {

    private final PickupRepository pickupRepository;
    private final HandoverService handoverService;

    @Value("${app.demo.auto-handover}")
    private boolean enabled;

    @Value("${app.demo.handover-photo-urls}")
    private List<String> photoUrls;

    @Value("${app.demo.customer-sign-url}")
    private String customerSignUrl;

    @Value("${app.demo.driver-sign-url}")
    private String driverSignUrl;

    @Scheduled(fixedDelay = 60_000)
    public void autoHandover() {

        if (!enabled) return;

        List<Long> targetIds = pickupRepository
                .findIdsForAutoHandover(PickupStatus.BOOKED, LocalDate.now());

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
