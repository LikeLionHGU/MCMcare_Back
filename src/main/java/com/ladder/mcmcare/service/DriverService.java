package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsPhoto;
import com.ladder.mcmcare.domain.Driver;
import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import com.ladder.mcmcare.dto.DriverDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.AsPhotoRepository;
import com.ladder.mcmcare.repository.DriverRepository;
import com.ladder.mcmcare.repository.PickupRepository;
import com.ladder.mcmcare.security.JwtProvider;
import com.ladder.mcmcare.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository driverRepository;
    private final PickupRepository pickupRepository;
    private final AsPhotoRepository asPhotoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public DriverDto.LoginResDto login(DriverDto.LoginReqDto req) {

        Driver driver = driverRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.getPassword(), driver.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.createToken(driver.getId(), driver.getLoginId(), Role.DRIVER);
        return DriverDto.LoginResDto.of(driver, token, jwtProvider.getExpiresInSeconds());
    }

    /**
     * 오늘의 수거 목록.
     * 고객 개인정보(이름·연락처·주소)가 포함되므로 당일 배정분으로 범위를 제한한다.
     */
    public DriverDto.PickupListResDto pickupList(Long driverId) {

        LocalDate today = LocalDate.now();

        List<Pickup> pickups = pickupRepository
                .findByDriverIdAndPickupDateAndStatusOrderBySlotStart(
                        driverId, today, PickupStatus.BOOKED);

        List<DriverDto.PickupItemDto> items = pickups.stream()
                .map(p -> DriverDto.PickupItemDto.of(p, asPhotoUrls(p)))
                .toList();

        return DriverDto.PickupListResDto.builder()
                .pickupDate(today)
                .itemList(items)
                .build();
    }

    private List<String> asPhotoUrls(Pickup p) {
        return asPhotoRepository.findByAsCaseIdOrderBySortOrder(p.getAsCase().getId())
                .stream().map(AsPhoto::getFileUrl).toList();
    }

    /** 인계 대상 조회 — 본인 배정 + 당일 건만 */
    public Pickup getAssignedToday(Long driverId, String pickupNo) {

        Pickup pickup = pickupRepository.findByPickupNo(pickupNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (!pickup.isAssignedTo(driverId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!pickup.getPickupDate().isEqual(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE);
        }
        return pickup;
    }
}
