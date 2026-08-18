package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.dto.AdminDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.AsCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PICKED_UP 이후 수선 센터 단계를 진행시키는 수단.
 *
 * 수거까지는 고객·기사 동작으로 진행된다.
 *   ESTIMATED      고객이 접수
 *   PICKUP_BOOKED  고객이 예약 확정
 *   PICKED_UP      기사 인계 (자동 인계 스케줄러 포함)
 *
 * 이 API 는 RECEIVED 이후만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AsCaseRepository asCaseRepository;
    private final AsStatusService asStatusService;

    @Transactional
    public AdminDto.UpdateStatusResDto updateStatus(String asNo, AdminDto.UpdateStatusReqDto req) {

        AsCase asCase = asCaseRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (asCase.getStatus() == AsStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        // 되돌리기 · 제자리 · 수거 전 입고를 막는다.
        // PICKED_UP 은 기사 인계로만 도달하므로 여기서는 허용되지 않는다.
        if (!asCase.getStatus().canAdminProgressTo(req.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        asCase.updateSchedule(req.getExpectedCompletedAt(), req.getDelayReason());
        asCase.updateLocation(req.getCurrentLocation(), req.getLocationType(), req.getLocationStatus());

        // completed_at 은 COMPLETED 전이 시 엔티티가 자동으로 기록한다
        asStatusService.transit(asCase, req.getStatus(), req.getDescription(), req.getStatusMessage());

        return AdminDto.UpdateStatusResDto.from(asCase);
    }
}
