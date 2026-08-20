package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.PhotoType;
import com.ladder.mcmcare.dto.AsCaseDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.service.port.EstimatePort;
import com.ladder.mcmcare.service.port.EstimateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** 접수번호 UNIQUE 충돌 시 재시도 횟수 */
    private static final int MAX_NO_RETRY = 3;

    private final AsCaseService asCaseService;
    private final FileService fileService;
    private final EstimatePort estimatePort;
    private final WarrantyEvaluator warrantyEvaluator;

    public AsCaseDto.CreateResDto create(Long memberId,
                                         AsCaseDto.CreateReqDto req,
                                         List<MultipartFile> images) {

        // 파일을 저장하기 전에 개수부터 확인한다.
        // 저장 후 검증하면 4장을 올렸을 때 4장 모두 디스크에 남는다.
        validatePhotoCount(images, req);

        List<String> photoUrls = fileService.upload(images, PHOTO_DIR);

        // ① TX — 접수 저장
        //
        // 접수번호는 동시 요청이 같은 값을 만들 수 있다. UNIQUE 위반이 나면 다시 시도하는데,
        // 재시도를 트랜잭션 안에서 하면 flush 실패로 오염된 세션을 이어 쓰게 된다.
        // 그래서 트랜잭션을 통째로 다시 연다.
        // DB 저장이 실패하면 방금 올린 파일을 지운다.
        // 접수가 만들어지지 않았으므로 그 파일을 참조할 방법이 없다.
        Long asId;
        try {
            asId = createDraftWithRetry(memberId, req, photoUrls);
        } catch (RuntimeException e) {
            fileService.deleteQuietly(photoUrls);
            throw e;
        }

        // ② TX — DRAFT → ANALYZING 선점
        //    분석 중임을 상태로 남겨야 이후 결과 반영을 안전하게 검증할 수 있다.
        asCaseService.claimForAnalysis(asId, AsStatus.DRAFT);

        // ③ TX 밖에서 AI 호출 → ④ TX 에서 결과 반영
        return analyzeAndApply(asId);
    }

    /**
     * 업로드 전 사전 검증.
     * 서비스 계층에서도 다시 확인하지만, 그때는 이미 파일이 저장된 뒤다.
     */
    private void validatePhotoCount(List<MultipartFile> images, AsCaseDto.CreateReqDto req) {

        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }
        if (images.size() > PhotoType.MAX_COUNT) {
            throw new BusinessException(ErrorCode.TOO_MANY_PHOTOS);
        }

        List<PhotoType> types = req.getPhotoTypeList();
        if (types == null || types.size() != images.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (types.contains(null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 전체 제품 사진이 없으면 AI 가 면적비를 계산하지 못해 심각도 판정이 부정확해진다
        if (!PhotoType.isValidCombination(types)) {
            throw new BusinessException(ErrorCode.PHOTO_TYPE_REQUIRED);
        }
    }

    /**
     * AI 를 호출하고 결과를 반영한다. 호출 전에 ANALYZING 으로 선점돼 있어야 한다.
     *
     * AI 호출은 트랜잭션 밖에서 한다 — 수십 초가 걸리므로 커넥션을 붙들면 안 된다.
     * 결과 반영과 실패 처리는 각각 짧은 트랜잭션에서 락을 잡고 상태를 다시 확인한다.
     */
    private AsCaseDto.CreateResDto analyzeAndApply(Long asId) {

        EstimateResult result;
        try {
            AsCase asCase = asCaseService.getById(asId);
            result = estimatePort.analyze(asCase, asCaseService.photosOf(asId));
        } catch (Exception e) {
            log.error("견적 분석 실패 asId={}", asId, e);
            asCaseService.markFailed(asId);
            throw new BusinessException(ErrorCode.ESTIMATE_FAILED, asCaseService.getAsNo(asId));
        }

        return asCaseService.applyEstimate(asId, result);
    }

    private Long createDraftWithRetry(Long memberId,
                                      AsCaseDto.CreateReqDto req,
                                      List<String> photoUrls) {
        for (int attempt = 0; ; attempt++) {
            try {
                return asCaseService.createDraft(memberId, req, photoUrls);
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_NO_RETRY) {
                    log.error("접수번호 채번 재시도 한도 초과", e);
                    throw e;
                }
                log.warn("접수번호 충돌 — 재시도 {}/{}", attempt + 1, MAX_NO_RETRY);
            }
        }
    }

    /**
     * 712 견적 조회.
     *
     * 접수 시점에 저장해 둔 결과를 읽는다. AI 를 다시 부르지 않는다.
     * 매번 재분석하면 같은 접수 건인데 열어볼 때마다 금액이 달라지고,
     * 조회에 수십 초가 걸린다.
     */
    public AsCaseDto.EstimateResDto estimate(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        // 분석 미완료(DRAFT · ANALYZING) · 실패(ESTIMATE_FAILED) · 취소 건은 조회할 수 없다.
        // 특히 ESTIMATE_FAILED 는 재분석 API 로만 처리해야 한다 —
        // 조회로도 AI 가 돌면 재시도 경로가 둘이 되어 상태 관리가 어긋난다.
        if (!asCase.getStatus().isEstimateViewable()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        EstimateResult result = asCaseService.storedEstimate(asCase.getId());
        return asCaseService.estimate(memberId, asNo, result);
    }

    /** 견적 재분석 — ESTIMATE_FAILED 상태에서만 */
    public AsCaseDto.RetryResDto retry(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        if (asCase.getStatus() == AsStatus.ESTIMATED) {
            throw new BusinessException(ErrorCode.ALREADY_ESTIMATED);
        }
        // ESTIMATE_FAILED → ANALYZING 선점.
        //
        // 선점하지 않으면 AI 호출 20초 사이에 고객이 취소할 수 있고
        // (ESTIMATE_FAILED 는 취소 가능 상태다), 나중에 도착한 결과가
        // 취소된 건을 ESTIMATED 로 되살린다.
        //
        // 실패하면 이미 다른 요청이 분석 중이거나 상태가 바뀐 것이다.
        if (!asCaseService.claimForAnalysis(asCase.getId(), AsStatus.ESTIMATE_FAILED)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        analyzeAndApply(asCase.getId());
        return AsCaseDto.RetryResDto.from(asCaseService.getById(asCase.getId()));
    }
}
