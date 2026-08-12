package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.dto.AsCaseDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.service.port.EstimatePort;
import com.ladder.mcmcare.service.port.EstimateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 호출을 트랜잭션 밖으로 빼기 위한 유일한 Facade.
 *
 * 715 화면구성 10번에 "이 과정에서 아마 시간 소요가 있을 예정이라 팝업 창도 하나 준비하기" 라고
 * 명시돼 있다. 즉 AI 호출은 수 초~수십 초가 걸린다.
 * 이를 @Transactional 안에 두면 그 시간 내내 DB 커넥션을 점유하므로
 * 동시 접속 시 커넥션 풀이 고갈된다.
 *
 * 그래서 트랜잭션을 3단계로 나눈다.
 *   ① TX  접수 저장 (DRAFT)
 *   ②     TX 밖에서 AI 호출 — 커넥션 반납 상태
 *   ③ TX  견적 반영 (ESTIMATED) 또는 실패 기록 (ESTIMATE_FAILED)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsCaseFacade {

    private static final String PHOTO_DIR = "as";

    private final AsCaseService asCaseService;
    private final FileService fileService;
    private final EstimatePort estimatePort;
    private final WarrantyEvaluator warrantyEvaluator;

    public AsCaseDto.CreateResDto create(Long memberId,
                                         AsCaseDto.CreateReqDto req,
                                         List<MultipartFile> images) {

        List<String> photoUrls = fileService.upload(images, PHOTO_DIR);

        // ① TX — 접수 저장
        Long asId = asCaseService.createDraft(memberId, req, photoUrls);

        // ② TX 밖 — 외부 I/O
        EstimateResult result;
        try {
            AsCase asCase = asCaseService.getById(asId);
            result = estimatePort.analyze(asCase, asCaseService.photosOf(asId));
        } catch (Exception e) {
            log.error("견적 분석 실패 asId={}", asId, e);
            asCaseService.markFailed(asId);                       // ③-a TX
            throw new BusinessException(ErrorCode.ESTIMATE_FAILED, asCaseService.getAsNo(asId));
        }

        // ③-b TX — 견적 반영
        return asCaseService.applyEstimate(asId, result);
    }

    /**
     * 712 견적 조회.
     * Phase 2 의 estimate 테이블이 없으므로 조회 시점에 재계산한다.
     * 테이블이 추가되면 저장된 값을 읽도록 바꾸면 된다.
     */
    public AsCaseDto.EstimateResDto estimate(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        // 비싼 AI 호출 전에 상태를 먼저 검증한다
        if (asCase.getStatus() == AsStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        EstimateResult result = estimatePort.analyze(asCase, asCaseService.photosOf(asCase.getId()));
        return asCaseService.estimate(memberId, asNo, result);
    }

    /** 견적 재분석 — ESTIMATE_FAILED 상태에서만 */
    public AsCaseDto.RetryResDto retry(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        if (asCase.getStatus() == AsStatus.ESTIMATED) {
            throw new BusinessException(ErrorCode.ALREADY_ESTIMATED);
        }
        if (asCase.getStatus() != AsStatus.ESTIMATE_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        EstimateResult result;
        try {
            result = estimatePort.analyze(asCase, asCaseService.photosOf(asCase.getId()));
        } catch (Exception e) {
            log.error("견적 재분석 실패 asNo={}", asNo, e);
            throw new BusinessException(ErrorCode.ESTIMATE_FAILED, asNo);
        }

        asCaseService.applyEstimate(asCase.getId(), result);
        return AsCaseDto.RetryResDto.from(asCaseService.getById(asCase.getId()));
    }
}
