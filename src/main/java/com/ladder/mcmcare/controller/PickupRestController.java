package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.dto.PickupDto;
import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import com.ladder.mcmcare.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pickup")
@RequiredArgsConstructor
public class PickupRestController {

    private final PickupService pickupService;

    /** 720 픽업 예약 화면 진입 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/form/{asNo}")
    public ResponseEntity<PickupDto.FormResDto> form(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(pickupService.form(principal.getId(), asNo));
    }

    /** 720 예약 가능 슬롯 */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping("/slot")
    public ResponseEntity<PickupDto.SlotResDto> slots(@RequestBody PickupDto.SlotReqDto reqDto) {
        return ResponseEntity.ok(pickupService.slots(reqDto));
    }

    /** 720 예약 확정 */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping("/{asNo}")
    public ResponseEntity<PickupDto.CreateResDto> create(
            @PathVariable String asNo,
            @Valid @RequestBody PickupDto.CreateReqDto reqDto,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(pickupService.create(principal.getId(), asNo, reqDto));
    }

    /** 713 예약 완료 화면 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/complete/{pickupNo}")
    public ResponseEntity<PickupDto.CompleteResDto> complete(
            @PathVariable String pickupNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(pickupService.detail(principal.getId(), pickupNo));
    }

    /** 예약 조회 (713 재진입) */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/{pickupNo}")
    public ResponseEntity<PickupDto.CompleteResDto> detail(
            @PathVariable String pickupNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(pickupService.detail(principal.getId(), pickupNo));
    }

    /** 예약 취소 */
    @PreAuthorize("hasRole('MEMBER')")
    @DeleteMapping("/{pickupNo}")
    public ResponseEntity<PickupDto.CancelResDto> cancel(
            @PathVariable String pickupNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(pickupService.cancel(principal.getId(), pickupNo));
    }
}
