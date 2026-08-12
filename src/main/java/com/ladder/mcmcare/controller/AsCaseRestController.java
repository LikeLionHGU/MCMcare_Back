package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.dto.AsCaseDto;
import com.ladder.mcmcare.dto.HandoverDto;
import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import com.ladder.mcmcare.service.AsCaseFacade;
import com.ladder.mcmcare.service.AsCaseService;
import com.ladder.mcmcare.service.HandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/asCase")
@RequiredArgsConstructor
public class AsCaseRestController {

    private final AsCaseService asCaseService;
    private final AsCaseFacade asCaseFacade;
    private final HandoverService handoverService;

    /** 715 접수 폼 초기 데이터 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/form")
    public ResponseEntity<AsCaseDto.FormResDto> form() {
        return ResponseEntity.ok(asCaseService.form());
    }

    /** 715 예상 견적 확인하기 — 접수 생성 + AI 분석 */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AsCaseDto.CreateResDto> create(
            @Valid @RequestPart("request") AsCaseDto.CreateReqDto reqDto,
            @RequestPart("images") List<MultipartFile> images,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseFacade.create(principal.getId(), reqDto, images));
    }

    /** 712 AI 예상 견적 결과 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/estimate/{asNo}")
    public ResponseEntity<AsCaseDto.EstimateResDto> estimate(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseFacade.estimate(principal.getId(), asNo));
    }

    /** 712 견적 재분석 */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping("/estimate/{asNo}")
    public ResponseEntity<AsCaseDto.RetryResDto> retry(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseFacade.retry(principal.getId(), asNo));
    }

    /** 710 나의 AS 목록 */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping("/list")
    public ResponseEntity<AsCaseDto.ListResDto> list(
            @RequestBody AsCaseDto.ListReqDto reqDto,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseService.list(principal.getId(), reqDto));
    }

    /** 716 나의 AS 상세 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/detail/{asNo}")
    public ResponseEntity<AsCaseDto.DetailResDto> detail(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseService.detail(principal.getId(), asNo));
    }

    /** 719 인계 완료 확인 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/handover/{asNo}")
    public ResponseEntity<HandoverDto.DetailResDto> handover(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {

        AsCase asCase = asCaseService.getOwned(principal.getId(), asNo);
        Pickup pickup = asCaseService.handoverTargetPickup(asCase);

        return ResponseEntity.ok(
                handoverService.detail(principal.getId(), asCase, pickup));
    }

    /** 접수 취소 */
    @PreAuthorize("hasRole('MEMBER')")
    @DeleteMapping("/{asNo}")
    public ResponseEntity<AsCaseDto.DeleteResDto> cancel(
            @PathVariable String asNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(asCaseService.cancel(principal.getId(), asNo));
    }
}
