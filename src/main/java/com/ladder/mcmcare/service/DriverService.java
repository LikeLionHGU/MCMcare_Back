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
    private final FileUrlSigner fileUrlSigner;

    public DriverDto.LoginResDto login(DriverDto.LoginReqDto req) {

        Driver driver = driverRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.getPassword(), driver.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 비활성 기사는 로그인할 수 없다.
        // 계정 상태를 알려주면 "이 아이디는 존재한다"는 사실이 드러나므로
        // 비밀번호 오류와 같은 응답을 내려보낸다.
        if (!driver.isActive()) {
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

        // JWT 는 상태를 갖지 않으므로 로그인 이후 비활성화되어도 토큰은 만료 전까지 유효하다.
        // 이 응답에는 고객 이름 · 연락처 · 주소가 포함되므로 매 요청마다 계정 상태를 확인한다.
        requireActive(driverId);

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

    /**
     * 계정이 여전히 유효한지 확인한다.
     * 토큰 발급 이후 비활성화된 기사를 걸러내기 위함이다.
     */
    private void requireActive(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_PERMISSION));
        if (!driver.isActive()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private List<String> asPhotoUrls(Pickup p) {
        return fileUrlSigner.sign(
                asPhotoRepository.findByAsCaseIdOrderBySortOrder(p.getAsCase().getId())
                        .stream().map(AsPhoto::getFileUrl).toList());
    }

    /**
     * 인계 가능 여부 사전 검증 — 본인 배정 + 당일 건만.
     *
     * 엔티티를 반환하지 않는다. 조회 트랜잭션 밖으로 나간 엔티티는 detached 가 되어
     * LAZY 초기화가 실패하고 상태 변경도 flush 되지 않기 때문이다.
     *
     * 파일을 저장하기 전에 권한 없는 요청을 걸러내는 것이 목적이며,
     * 실제 처리 시점의 검증은 HandoverService.completeByDriver 가 트랜잭션 안에서 다시 수행한다.
     */
    public void verifyAssignableToday(Long driverId, String pickupNo) {

        requireActive(driverId);

        Pickup pickup = pickupRepository.findByPickupNo(pickupNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (!pickup.isAssignedTo(driverId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!pickup.getPickupDate().isEqual(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_PICKUP_DATE);
        }
        if (pickup.getStatus() != PickupStatus.BOOKED) {
            throw new BusinessException(ErrorCode.ALREADY_HANDED_OVER);
        }
    }
}
