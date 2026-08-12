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
 * 수거까지는 고객·기사 동작으로 자동 진행되며, 입고 이후는 이 API 로 갱신한다.
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

        asCase.updateSchedule(req.getExpectedCompletedAt(), req.getDelayReason());
        asCase.updateLocation(req.getCurrentLocation(), req.getLocationType(), req.getLocationStatus());

        // completed_at 은 COMPLETED 전이 시 엔티티가 자동으로 기록한다
        asStatusService.transit(asCase, req.getStatus(), req.getDescription(), req.getStatusMessage());

        return AdminDto.UpdateStatusResDto.from(asCase);
    }
}
