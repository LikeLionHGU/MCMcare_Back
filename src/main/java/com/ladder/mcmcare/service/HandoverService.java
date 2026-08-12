package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.Handover;
import com.ladder.mcmcare.domain.HandoverPhoto;
import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import com.ladder.mcmcare.dto.HandoverDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.HandoverPhotoRepository;
import com.ladder.mcmcare.repository.HandoverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인계 처리.
 *
 * 기사가 직접 호출하든 자동 인계 스케줄러가 호출하든 이 메서드를 거치므로
 * 어느 경로에서든 결과가 동일하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverService {

    private final HandoverRepository handoverRepository;
    private final HandoverPhotoRepository handoverPhotoRepository;
    private final AsStatusService asStatusService;

    /**
     * 인계 완료. 동일 트랜잭션에서 네 가지가 함께 처리된다.
     *   1. handover 생성 — 기사 정보를 인계 시점 스냅샷으로 복사
     *   2. handover_photo 생성
     *   3. pickup.status → COMPLETED
     *   4. as_case.status → PICKED_UP + 이력 기록
     *
     * handedOverAt 은 서버가 기록한다. 기기 시계 오차·조작을 배제하기 위함이며
     * 이 값은 분쟁 시 참고 자료가 된다.
     */
    @Transactional
    public LocalDateTime complete(Pickup pickup,
                                  List<String> photoUrls,
                                  String customerSignUrl,
                                  String driverSignUrl) {

        if (pickup.getStatus() != PickupStatus.BOOKED) {
            throw new BusinessException(ErrorCode.ALREADY_HANDED_OVER);
        }
        if (photoUrls == null || photoUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }
        if (customerSignUrl == null || driverSignUrl == null) {
            throw new BusinessException(ErrorCode.SIGN_REQUIRED);
        }

        Handover handover = handoverRepository.save(
                Handover.of(pickup, pickup.getDriver(), customerSignUrl, driverSignUrl));

        for (int i = 0; i < photoUrls.size(); i++) {
            handoverPhotoRepository.save(HandoverPhoto.of(handover, photoUrls.get(i), i));
        }

        pickup.complete();
        asStatusService.transit(pickup.getAsCase(), AsStatus.PICKED_UP,
                "기사 인계 완료 · 수선 센터로 이동");

        return handover.getHandedOverAt();
    }

    /** 719 인계 완료 확인 */
    public HandoverDto.DetailResDto detail(Long memberId, AsCase asCase, Pickup pickup) {

        if (!asCase.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Handover handover = handoverRepository.findByPickupId(pickup.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        List<String> urls = handoverPhotoRepository
                .findByHandoverIdOrderBySortOrder(handover.getId())
                .stream().map(HandoverPhoto::getFileUrl).toList();

        return HandoverDto.DetailResDto.of(handover, urls);
    }
}
