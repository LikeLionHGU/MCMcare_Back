package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.Pickup;
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
 * 기사 화면이 배포되기 전까지 서버가 인계를 대신 수행한다.
 * HandoverService.complete() 를 그대로 호출하므로
 * 자동·수동 어느 경로든 결과가 동일하다.
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

        List<Pickup> targets = pickupRepository
                .findByStatusAndPickupDateLessThanEqual(PickupStatus.BOOKED, LocalDate.now());

        for (Pickup pickup : targets) {
            if (pickup.getDriver() == null) continue;
            try {
                handoverService.complete(pickup, photoUrls, customerSignUrl, driverSignUrl);
                log.info("자동 인계 완료 pickupNo={}", pickup.getPickupNo());
            } catch (Exception e) {
                log.warn("자동 인계 실패 pickupNo={}", pickup.getPickupNo(), e);
            }
        }
    }
}
