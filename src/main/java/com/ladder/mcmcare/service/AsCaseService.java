package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.AsStatusHistory;
import com.ladder.mcmcare.domain.DamageType;
import com.ladder.mcmcare.domain.Member;
import com.ladder.mcmcare.domain.PhotoType;
import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupStatus;
import com.ladder.mcmcare.domain.Product;
import com.ladder.mcmcare.domain.ProductType;
import com.ladder.mcmcare.domain.PurchaseChannel;
import com.ladder.mcmcare.dto.AsCaseDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.AsCaseRepository;
import com.ladder.mcmcare.repository.AsPhotoRepository;
import com.ladder.mcmcare.repository.AsStatusHistoryRepository;
import com.ladder.mcmcare.repository.MemberRepository;
import com.ladder.mcmcare.repository.PickupRepository;
import com.ladder.mcmcare.repository.ProductRepository;
import com.ladder.mcmcare.service.port.EstimateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsCaseService {

    private static final int MAX_PHOTOS = 3;

    /** 접수번호 UNIQUE 충돌 시 재시도 횟수 */
    private static final int MAX_NO_RETRY = 3;

    private final AsCaseRepository asCaseRepository;
    private final AsPhotoRepository asPhotoRepository;
    private final AsStatusHistoryRepository historyRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final PickupRepository pickupRepository;
    private final AsStatusService asStatusService;
    private final WarrantyEvaluator warrantyEvaluator;
    private final NumberGenerator numberGenerator;

    // ── Form ─────────────────────────────────────────────────────

    public AsCaseDto.FormResDto form() {
        return AsCaseDto.FormResDto.builder()
                .productTypeList(Arrays.stream(ProductType.values())
                        .map(t -> AsCaseDto.CodeDto.of(t.name(), t.getLabel())).toList())
                .purchaseChannelList(Arrays.stream(PurchaseChannel.values())
                        .map(t -> AsCaseDto.CodeDto.of(t.name(), t.getLabel())).toList())
                .damageTypeList(Arrays.stream(DamageType.values())
                        .map(t -> AsCaseDto.CodeDto.of(t.name(), t.getLabel())).toList())
                .build();
    }

    // ── Create (1단계: 접수 저장) ────────────────────────────────

    /**
     * AI 호출 전에 접수 건만 저장한다.
     * 분석이 실패해도 접수는 남아 재시도할 수 있다.
     */
    @Transactional
    public Long createDraft(Long memberId, AsCaseDto.CreateReqDto req, List<String> photoUrls) {

        if (photoUrls.isEmpty()) throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        if (photoUrls.size() > MAX_PHOTOS) throw new BusinessException(ErrorCode.TOO_MANY_PHOTOS);

        List<PhotoType> types = req.getPhotoTypeList();
        if (types == null || types.size() != photoUrls.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        Product product = (req.getWarrantyNo() == null || req.getWarrantyNo().isBlank())
                ? null
                : productRepository.findById(req.getWarrantyNo()).orElse(null);

        AsCase asCase = AsCase.builder()
                .asNo(numberGenerator.generateAsNo())
                .member(member)
                .product(product)
                .productType(req.getProductType())
                .modelName(req.getModelName())
                .purchasedAt(req.getPurchasedAt())
                .purchaseChannel(req.getPurchaseChannel())
                .damagePart(req.getDamagePart())
                .damageType(req.getDamageType())
                .damageDescription(req.getDamageDescription())
                .build();

        // 동시 요청이 같은 번호를 만들 수 있다. existsByAsNo 로는 미커밋 트랜잭션을 볼 수 없으므로
        // UNIQUE 위반을 잡아 번호를 다시 받는다.
        for (int attempt = 0; ; attempt++) {
            try {
                asCaseRepository.saveAndFlush(asCase);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_NO_RETRY) throw e;
                asCase.reassignAsNo(numberGenerator.generateAsNo());
            }
        }

        for (int i = 0; i < photoUrls.size(); i++) {
            asPhotoRepository.save(AsPhoto.of(asCase, photoUrls.get(i), types.get(i), i));
        }

        return asCase.getId();
    }

    // ── Create (3단계: 견적 반영) ────────────────────────────────

    @Transactional
    public AsCaseDto.CreateResDto applyEstimate(Long asId, EstimateResult result) {
        AsCase asCase = getById(asId);
        asStatusService.transit(asCase, AsStatus.ESTIMATED, "AI 예상 견적 산출 완료");
        // 견적 상세는 Phase 2 의 estimate 테이블에 저장된다.
        // 현재는 조회 시점에 재계산하므로 별도 영속화가 없다.
        return AsCaseDto.CreateResDto.from(asCase);
    }

    @Transactional
    public void markFailed(Long asId) {
        AsCase asCase = getById(asId);
        asStatusService.transit(asCase, AsStatus.ESTIMATE_FAILED, "AI 분석 실패");
    }

    public String getAsNo(Long asId) {
        return getById(asId).getAsNo();
    }

    // ── Estimate 조회 ────────────────────────────────────────────

    public AsCaseDto.EstimateResDto estimate(Long memberId, String asNo, EstimateResult result) {
        AsCase asCase = getOwned(memberId, asNo);

        if (asCase.getStatus() == AsStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        List<String> urls = asPhotoRepository.findByAsCaseIdOrderBySortOrder(asCase.getId())
                .stream().map(AsPhoto::getFileUrl).toList();

        return AsCaseDto.EstimateResDto.of(asCase, urls, result, warrantyEvaluator.evaluate(asCase));
    }

    // ── List (710) ───────────────────────────────────────────────

    public AsCaseDto.ListResDto list(Long memberId, AsCaseDto.ListReqDto req) {

        List<AsStatus> targets = statusesOf(req.filterOrDefault());

        Page<AsCase> page = asCaseRepository.findByMemberIdAndStatusInOrderByCreatedAtDesc(
                memberId, targets,
                PageRequest.of(req.pageOrDefault(), req.sizeOrDefault()));

        long inProgress = asCaseRepository.countByMemberIdAndStatusIn(
                memberId, AsStatus.inProgressValues());
        long completed = asCaseRepository.countByMemberIdAndStatusIn(
                memberId, List.of(AsStatus.COMPLETED));

        LocalDate lastUpdated = asCaseRepository.findLastUpdatedAt(memberId)
                .map(java.time.LocalDateTime::toLocalDate).orElse(null);

        return AsCaseDto.ListResDto.builder()
                .inProgressCount(inProgress)
                .completedCount(completed)
                .lastUpdatedAt(lastUpdated)
                .itemList(page.getContent().stream().map(AsCaseDto.ListItemDto::from).toList())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    /** 취소 건은 어떤 필터에서도 조회되지 않는다. */
    private List<AsStatus> statusesOf(String filter) {
        return switch (filter) {
            case "IN_PROGRESS" -> AsStatus.inProgressValues();
            case "COMPLETED"   -> List.of(AsStatus.COMPLETED);
            default -> Arrays.stream(AsStatus.values())
                    .filter(s -> s != AsStatus.CANCELLED).toList();
        };
    }

    // ── Detail (716) ─────────────────────────────────────────────

    public AsCaseDto.DetailResDto detail(Long memberId, String asNo) {

        AsCase c = getOwned(memberId, asNo);

        String pickupNo = pickupRepository
                .findFirstByAsCaseIdAndStatusInOrderByCreatedAtDesc(
                        c.getId(), List.of(PickupStatus.BOOKED, PickupStatus.COMPLETED))
                .map(Pickup::getPickupNo).orElse(null);

        return AsCaseDto.DetailResDto.builder()
                .asNo(c.getAsNo())
                .modelName(c.getModelName())
                .createdAt(c.getCreatedAt().toLocalDate())
                .intakeType(c.getIntakeType())
                .pickupNo(pickupNo)
                .status(c.getStatus().name())
                .statusLabel(c.getStatus().getLabel())
                .statusUpdatedAt(c.getStatusUpdatedAt())
                .statusMessage(c.getStatusMessage())
                .expectedCompletedAt(c.getExpectedCompletedAt())
                .expectedUpdatedAt(c.getExpectedUpdatedAt())
                .delayReason(c.getDelayReason())
                .currentLocation(c.getCurrentLocation())
                .locationType(c.getLocationType())
                .locationStatus(c.getLocationStatus())
                .historyList(buildTimeline(c))
                .build();
    }

    /**
     * 발생한 이력 + 아직 오지 않은 단계를 함께 반환한다.
     * 716 화면이 6단계를 전부 보여주기 때문이다.
     */
    private List<AsCaseDto.HistoryItemDto> buildTimeline(AsCase c) {

        List<AsStatusHistory> done = historyRepository.findByAsCaseIdOrderByOccurredAt(c.getId());
        List<AsCaseDto.HistoryItemDto> timeline = new ArrayList<>();

        List<AsStatus> doneStatuses = done.stream().map(AsStatusHistory::getStatus).toList();
        done.stream()
                .filter(h -> AsStatus.TIMELINE.contains(h.getStatus()))
                .forEach(h -> timeline.add(AsCaseDto.HistoryItemDto.done(h)));

        AsStatus.TIMELINE.stream()
                .filter(s -> !doneStatuses.contains(s))
                .forEach(s -> timeline.add(AsCaseDto.HistoryItemDto.pending(s)));

        return timeline;
    }

    // ── Handover 조회 (719) ─────────────────────────────────────

    /**
     * 인계 기록 조회용 픽업 선택.
     * as_case 는 픽업을 여러 건 가질 수 있으므로(취소 후 재예약),
     * BOOKED 또는 COMPLETED 중 가장 최근 건을 반환한다.
     */
    public Pickup handoverTargetPickup(AsCase asCase) {
        return pickupRepository
                .findFirstByAsCaseIdAndStatusInOrderByCreatedAtDesc(
                        asCase.getId(), List.of(PickupStatus.BOOKED, PickupStatus.COMPLETED))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
    }

    // ── Delete (접수 취소) ───────────────────────────────────────

    @Transactional
    public AsCaseDto.DeleteResDto cancel(Long memberId, String asNo) {

        AsCase c = getOwned(memberId, asNo);

        if (c.getStatus() != AsStatus.ESTIMATED && c.getStatus() != AsStatus.ESTIMATE_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (pickupRepository.existsByAsCaseIdAndStatus(c.getId(), PickupStatus.BOOKED)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        // 하드 삭제하면 pickup · handover · as_photo 가 FK 로 연쇄되어 실패한다
        asStatusService.transit(c, AsStatus.CANCELLED, "고객 요청으로 접수 취소");
        return AsCaseDto.DeleteResDto.from(c);
    }

    // ── 내부 ─────────────────────────────────────────────────────

    public AsCase getById(Long asId) {
        return asCaseRepository.findById(asId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
    }

    public AsCase getOwned(Long memberId, String asNo) {
        AsCase c = asCaseRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        if (!c.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return c;
    }

    public List<AsPhoto> photosOf(Long asId) {
        return asPhotoRepository.findByAsCaseIdOrderBySortOrder(asId);
    }
}
