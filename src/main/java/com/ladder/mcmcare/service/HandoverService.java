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
import com.ladder.mcmcare.repository.PickupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    /** 인계 전 제품 상태 사진 최대 장수 */
    private static final int MAX_HANDOVER_PHOTOS = 5;

    private final HandoverRepository handoverRepository;
    private final HandoverPhotoRepository handoverPhotoRepository;
    private final PickupRepository pickupRepository;
    private final AsStatusService asStatusService;
    private final AsCaseService asCaseService;
    private final FileUrlSigner fileUrlSigner;

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
    /**
     * 자동 인계 스케줄러 진입점.
     *
     * 트랜잭션 안에서 pickup 을 다시 조회해 managed 상태로 만들고, 행을 잠근다.
     * 락이 없으면 아래 complete() 의 상태 검사가 무의미해진다 —
     * 고객 취소가 같은 행을 잠그고 CANCELLED 로 바꾸는 사이 이쪽은 BOOKED 를 읽고
     * 그대로 인계를 진행할 수 있다.
     *
     * 수동 인계(completeByDriver)와 동일한 락 전략을 쓴다.
     */
    @Transactional
    public LocalDateTime completeById(Long pickupId,
                                      List<String> photoUrls,
                                      String customerSignUrl,
                                      String driverSignUrl) {
        Pickup pickup = pickupRepository.findByIdForUpdate(pickupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        return complete(pickup, photoUrls, customerSignUrl, driverSignUrl);
    }

    /**
     * 기사 수동 인계 진입점.
     *
     * 컨트롤러가 엔티티를 넘기지 않고 번호만 넘긴다.
     * 조회 트랜잭션이 끝난 엔티티(detached)를 받으면
     * LAZY 초기화가 실패하고 상태 변경도 flush 되지 않기 때문이다.
     *
     * 권한·날짜 검증도 이 트랜잭션 안에서 다시 수행한다.
     * 파일 업로드 사이에 상태가 바뀌었을 수 있다.
     */
    @Transactional
    public LocalDateTime completeByDriver(Long driverId,
                                          String pickupNo,
                                          List<String> photoUrls,
                                          String customerSignUrl,
                                          String driverSignUrl) {

        Pickup pickup = pickupRepository.findByPickupNoForUpdate(pickupNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (!pickup.isAssignedTo(driverId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!pickup.getDriver().isActive()) {
            // 사전 검증 이후 비활성화되었을 수 있다
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!pickup.getPickupDate().isEqual(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE);
        }

        return complete(pickup, photoUrls, customerSignUrl, driverSignUrl);
    }

    /**
     * 실제 인계 처리.
     * managed 엔티티만 받을 수 있도록 private 으로 닫는다.
     * 외부에서는 completeById / completeByDriver 로만 진입한다.
     */
    private LocalDateTime complete(Pickup pickup,
                                  List<String> photoUrls,
                                  String customerSignUrl,
                                  String driverSignUrl) {

        if (pickup.getStatus() != PickupStatus.BOOKED) {
            throw new BusinessException(ErrorCode.ALREADY_HANDED_OVER);
        }
        if (pickup.getDriver() == null) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_DRIVER);
        }
        // 수동·자동 경로가 동시에 들어와도 UNIQUE 제약 이전에 걸러낸다
        if (handoverRepository.existsByPickupId(pickup.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_HANDED_OVER);
        }
        if (photoUrls == null || photoUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }
        if (photoUrls.size() > MAX_HANDOVER_PHOTOS) {
            throw new BusinessException(ErrorCode.TOO_MANY_HANDOVER_PHOTOS);
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

    /**
     * 719 인계 완료 확인.
     *
     * 컨트롤러가 엔티티를 넘기지 않고 접수번호만 넘긴다.
     * 조회 트랜잭션이 끝난 엔티티는 detached 라, 소유자 확인에 쓰이는
     * member 프록시 초기화조차 실패한다 (@Id 가 필드에 있어 getId() 도 초기화를 유발한다).
     */
    public HandoverDto.DetailResDto detailByAsNo(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);
        Pickup pickup = asCaseService.handoverTargetPickup(asCase);

        Handover handover = handoverRepository.findByPickupId(pickup.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        List<String> urls = fileUrlSigner.sign(
                handoverPhotoRepository.findByHandoverIdOrderBySortOrder(handover.getId())
                        .stream().map(HandoverPhoto::getFileUrl).toList());

        // 서명 이미지도 개인정보이므로 함께 서명한다
        return HandoverDto.DetailResDto.of(handover, urls,
                fileUrlSigner.sign(handover.getCustomerSignUrl()),
                fileUrlSigner.sign(handover.getDriverSignUrl()));
    }
}
