package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.dto.DriverDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import com.ladder.mcmcare.service.DriverService;
import com.ladder.mcmcare.service.FileService;
import com.ladder.mcmcare.service.HandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기사가 현장에서 수행하는 최소 동작만 제공한다 — 로그인 · 오늘 수거 목록 · 인계 완료.
 * 배차는 예약 확정 시 서버가 자동 수행하므로 건별 수락 절차가 없다.
 */
@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverRestController {

    private static final String HANDOVER_DIR = "handover";
    private static final String SIGN_DIR = "sign";

    /** 인계 전 제품 상태 사진 최대 장수 */
    private static final int MAX_HANDOVER_PHOTOS = 5;

    private final DriverService driverService;
    private final HandoverService handoverService;
    private final FileService fileService;

    /** 기사 로그인 */
    @PostMapping("/login")
    public ResponseEntity<DriverDto.LoginResDto> login(
            @Valid @RequestBody DriverDto.LoginReqDto reqDto) {
        return ResponseEntity.ok(driverService.login(reqDto));
    }

    /** 오늘의 수거 목록 */
    @PreAuthorize("hasRole('DRIVER')")
    @GetMapping("/pickup/list")
    public ResponseEntity<DriverDto.PickupListResDto> pickupList(
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(driverService.pickupList(principal.getId()));
    }

    /** 인계 완료 — 사진 · 서명 업로드 */
    @PreAuthorize("hasRole('DRIVER')")
    @PostMapping(value = "/handover/{pickupNo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DriverDto.HandoverResDto> handover(
            @PathVariable String pickupNo,
            @RequestPart("photos") List<MultipartFile> photos,
            @RequestPart("customerSign") MultipartFile customerSign,
            @RequestPart("driverSign") MultipartFile driverSign,
            @LoginMember PrincipalDetails principal) {

        // 파일을 저장하기 전에 권한과 개수를 먼저 확인한다.
        // 저장 후 검증하면 초과분이 디스크에 그대로 남는다.
        driverService.verifyAssignableToday(principal.getId(), pickupNo);

        if (photos == null || photos.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }
        if (photos.size() > MAX_HANDOVER_PHOTOS) {
            throw new BusinessException(ErrorCode.TOO_MANY_HANDOVER_PHOTOS);
        }

        // 엔티티가 아니라 번호를 넘긴다.
        // 조회 트랜잭션이 끝난 엔티티는 detached 라 LAZY 초기화와 flush 가 실패한다.
        LocalDateTime handedOverAt = handoverService.completeByDriver(
                principal.getId(),
                pickupNo,
                fileService.upload(photos, HANDOVER_DIR),
                fileService.upload(customerSign, SIGN_DIR),
                fileService.upload(driverSign, SIGN_DIR));

        return ResponseEntity.ok(DriverDto.HandoverResDto.builder()
                .pickupNo(pickupNo)
                .handedOverAt(handedOverAt)
                .build());
    }
}
