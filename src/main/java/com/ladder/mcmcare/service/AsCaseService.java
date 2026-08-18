package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.Estimate;
import com.ladder.mcmcare.domain.EstimateItem;
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
import com.ladder.mcmcare.repository.EstimateItemRepository;
import com.ladder.mcmcare.repository.EstimateRepository;
import com.ladder.mcmcare.repository.AsPhotoRepository;
import com.ladder.mcmcare.repository.AsStatusHistoryRepository;
import com.ladder.mcmcare.repository.MemberRepository;
import com.ladder.mcmcare.repository.PickupRepository;
import com.ladder.mcmcare.repository.ProductRepository;
import com.ladder.mcmcare.service.port.EstimateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsCaseService {

    private final AsCaseRepository asCaseRepository;
    private final AsPhotoRepository asPhotoRepository;
    private final AsStatusHistoryRepository historyRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final PickupRepository pickupRepository;
    private final AsStatusService asStatusService;
    private final WarrantyEvaluator warrantyEvaluator;
    private final NumberGenerator numberGenerator;
    private final FileUrlSigner fileUrlSigner;
    private final EstimateRepository estimateRepository;
    private final EstimateItemRepository estimateItemRepository;

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
        if (photoUrls.size() > PhotoType.MAX_COUNT) throw new BusinessException(ErrorCode.TOO_MANY_PHOTOS);

        List<PhotoType> types = req.getPhotoTypeList();
        if (types == null || types.size() != photoUrls.size() || types.contains(null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!PhotoType.isValidCombination(types)) {
            throw new BusinessException(ErrorCode.PHOTO_TYPE_REQUIRED);
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

        // 동시 요청이 같은 번호를 만들 수 있다. existsByAsNo 로는 미커밋 트랜잭션을 볼 수 없다.
        // UNIQUE 위반 시 재시도는 AsCaseFacade 가 담당한다 —
        // flush 실패로 오염된 세션에서 조회·저장을 이어가면 동작을 보장할 수 없기 때문이다.
        asCaseRepository.saveAndFlush(asCase);

        for (int i = 0; i < photoUrls.size(); i++) {
            asPhotoRepository.save(AsPhoto.of(asCase, photoUrls.get(i), types.get(i), i));
        }

        return asCase.getId();
    }

    // ── Create (3단계: 견적 반영) ────────────────────────────────

    /**
     * AI 분석 결과를 저장하고 상태를 전이한다.
     *
     * 저장하지 않으면 조회할 때마다 AI 를 다시 호출해야 한다.
     * 같은 접수 건인데 열어볼 때마다 금액이 달라지고, 조회에 수십 초가 걸린다.
     *
     * 재분석이면 이전 견적을 지우고 새로 쓴다. 이력은 남기지 않는다.
     */
    @Transactional
    public AsCaseDto.CreateResDto applyEstimate(Long asId, EstimateResult result) {

        AsCase asCase = getById(asId);
        saveEstimate(asCase, result);
        asStatusService.transit(asCase, AsStatus.ESTIMATED, "AI 예상 견적 산출 완료");
        return AsCaseDto.CreateResDto.from(asCase);
    }

    private void saveEstimate(AsCase asCase, EstimateResult result) {

        // 재분석 시 이전 건을 먼저 지운다. as_id 에 UNIQUE 가 걸려 있어 덮어쓸 수 없다.
        //
        // 항목을 명시적으로 지우는 이유 — 항목은 estimateItemRepository.save() 로 따로 저장하므로
        // estimate.items 컬렉션이 비어 있을 수 있다. cascade 에만 맡기면 FK 위반이 날 수 있다.
        // DB 의 fk_item_estimate 는 RESTRICT 라 순서를 지켜야 한다.
        estimateRepository.findByAsCaseId(asCase.getId()).ifPresent(prev -> {
            estimateItemRepository.deleteByEstimateId(prev.getId());
            estimateItemRepository.flush();
            estimateRepository.delete(prev);
            estimateRepository.flush();
        });

        Estimate estimate = estimateRepository.save(Estimate.builder()
                .asCase(asCase)
                .damageCategory(result.getDamageCategory())
                .damageSeverity(result.getDamageSeverity())
                .confidenceGrade(result.getConfidenceGrade())
                .confidenceNote(result.getConfidenceNote())
                .noDamageNotice(result.getNoDamageNotice())
                .rawResponse(result.getRawResponse())
                .build());

        List<EstimateResult.Item> items = result.getItems();
        for (int i = 0; i < items.size(); i++) {
            EstimateResult.Item item = items.get(i);
            estimate.addItem(estimateItemRepository.save(EstimateItem.of(
                    estimate, item.getRepairItemName(),
                    item.getEstimatedPrice(), item.getMinPrice(), item.getMaxPrice(), i)));
        }
    }

    /**
     * AI 분석 도중 서버가 중단되어 DRAFT · ANALYZING 으로 남은 건을 정리한다.
     *
     * 그대로 두면 목록에도 안 보이고 재분석 · 취소도 되지 않아
     * 사용자가 손댈 수 없는 상태로 영원히 남는다.
     * ESTIMATE_FAILED 로 바꾸면 목록에 나타나고 재시도할 수 있다.
     *
     * @return 정리한 건수
     */
    @Transactional
    public int recoverStaleDrafts(int staleMinutes) {

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleMinutes);
        List<Long> staleIds = asCaseRepository.findStaleIds(
                List.of(AsStatus.DRAFT, AsStatus.ANALYZING), threshold);

        for (Long asId : staleIds) {
            AsCase asCase = getById(asId);
            asStatusService.transit(asCase, AsStatus.ESTIMATE_FAILED,
                    "분석이 완료되지 않아 실패 처리되었습니다");
        }
        return staleIds.size();
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

    /**
     * 저장된 견적을 읽어 화면 계약으로 복원한다.
     * 없으면 아직 분석이 끝나지 않은 것이다.
     */
    public EstimateResult storedEstimate(Long asId) {

        Estimate e = estimateRepository.findByAsCaseId(asId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        List<EstimateResult.Item> items = estimateItemRepository
                .findByEstimateIdOrderBySortOrder(e.getId())
                .stream()
                .map(i -> EstimateResult.Item.builder()
                        .repairItemName(i.getRepairItemName())
                        .estimatedPrice(i.getEstimatedPrice())
                        .minPrice(i.getMinPrice())
                        .maxPrice(i.getMaxPrice())
                        .build())
                .toList();

        return EstimateResult.builder()
                .damageCategory(e.getDamageCategory())
                .damageSeverity(e.getDamageSeverity())
                .confidenceGrade(e.getConfidenceGrade())
                .confidenceNote(e.getConfidenceNote())
                .noDamageNotice(e.getNoDamageNotice())
                .items(items)
                .build();
    }

    public AsCaseDto.EstimateResDto estimate(Long memberId, String asNo, EstimateResult result) {
        AsCase asCase = getOwned(memberId, asNo);

        if (!asCase.getStatus().isEstimateViewable()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        // DB 에는 서명 없는 URL 이 저장돼 있다. 응답 시점에 서명을 붙여 내려준다.
        List<String> urls = fileUrlSigner.sign(
                asPhotoRepository.findByAsCaseIdOrderBySortOrder(asCase.getId())
                        .stream().map(AsPhoto::getFileUrl).toList());

        return AsCaseDto.EstimateResDto.of(asCase, urls, result, warrantyEvaluator.evaluate(asCase));
    }

    // ── List (710) ───────────────────────────────────────────────

    public AsCaseDto.ListResDto list(Long memberId, AsCaseDto.ListReqDto req) {

        List<AsStatus> targets = statusesOf(req.filterOrDefault());

        Page<AsCase> page = asCaseRepository.findByMemberIdAndStatusInOrderByCreatedAtDesc(
                memberId, targets,
                PageRequest.of(req.pageOrDefault(), req.sizeOrDefault()));

        long inProgress = asCaseRepository.countByMemberIdAndStatusIn(
                memberId, AsStatus.visibleInProgressValues());
        long completed = asCaseRepository.countByMemberIdAndStatusIn(
                memberId, List.of(AsStatus.COMPLETED));

        LocalDate lastUpdated = asCaseRepository.findLastUpdatedAt(memberId)
                .map(LocalDateTime::toLocalDate).orElse(null);

        Map<Long, String> thumbnails = thumbnailsOf(page.getContent());

        return AsCaseDto.ListResDto.builder()
                .inProgressCount(inProgress)
                .completedCount(completed)
                .lastUpdatedAt(lastUpdated)
                .itemList(page.getContent().stream()
                        .map(c -> AsCaseDto.ListItemDto.of(c, thumbnails.get(c.getId())))
                        .toList())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    /**
     * 취소 건과 중간 상태(DRAFT · ANALYZING)는 어떤 필터에서도 조회되지 않는다.
     * 중간 상태는 정상 흐름에서 수 초 안에 사라지며, 남은 건은 스케줄러가 정리한다.
     */
    /**
     * 목록에 실을 대표 사진(첫 장)을 접수 건별로 모은다.
     * 건마다 조회하면 목록 크기만큼 쿼리가 나가므로 한 번에 가져온다.
     */
    private Map<Long, String> thumbnailsOf(List<AsCase> cases) {

        if (cases.isEmpty()) return Map.of();

        List<Long> asIds = cases.stream().map(AsCase::getId).toList();
        Map<Long, String> result = new LinkedHashMap<>();

        // sortOrder 오름차순으로 정렬돼 오므로 접수 건마다 첫 장만 남는다
        for (AsPhoto photo : asPhotoRepository.findByAsCaseIdInOrderByAsCaseIdAscSortOrderAsc(asIds)) {
            result.putIfAbsent(photo.getAsCase().getId(), fileUrlSigner.sign(photo.getFileUrl()));
        }
        return result;
    }

    private List<AsStatus> statusesOf(String filter) {
        return switch (filter) {
            case "IN_PROGRESS" -> AsStatus.visibleInProgressValues();
            case "COMPLETED"   -> List.of(AsStatus.COMPLETED);
            default            -> AsStatus.visibleValues();
        };
    }

    // ── Detail (716) ─────────────────────────────────────────────

    public AsCaseDto.DetailResDto detail(Long memberId, String asNo) {

        AsCase c = getOwned(memberId, asNo);

        String pickupNo = pickupRepository
                .findFirstByAsCaseIdAndStatusInOrderByCreatedAtDesc(
                        c.getId(), List.of(PickupStatus.BOOKED, PickupStatus.COMPLETED))
                .map(Pickup::getPickupNo).orElse(null);

        List<String> photoUrls = fileUrlSigner.sign(
                asPhotoRepository.findByAsCaseIdOrderBySortOrder(c.getId())
                        .stream().map(AsPhoto::getFileUrl).toList());

        return AsCaseDto.DetailResDto.builder()
                .asNo(c.getAsNo())
                .modelName(c.getModelName())
                .createdAt(c.getCreatedAt().toLocalDate())
                .intakeType(c.getIntakeType())
                .pickupNo(pickupNo)
                .photoUrlList(photoUrls)
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
     *
     * 취소된 접수는 예정 단계를 붙이지 않는다.
     * 이미 끝난 건에 "수선 진행 중 예정"이 남으면 앞으로 수선할 것처럼 보인다.
     * 대신 취소 이력을 마지막에 노출한다.
     */
    private List<AsCaseDto.HistoryItemDto> buildTimeline(AsCase c) {

        List<AsStatusHistory> done = historyRepository.findByAsCaseIdOrderByOccurredAt(c.getId());
        List<AsCaseDto.HistoryItemDto> timeline = new ArrayList<>();

        done.stream()
                .filter(h -> AsStatus.TIMELINE.contains(h.getStatus()))
                .forEach(h -> timeline.add(AsCaseDto.HistoryItemDto.done(h)));

        if (c.getStatus() == AsStatus.CANCELLED) {
            done.stream()
                    .filter(h -> h.getStatus() == AsStatus.CANCELLED)
                    .forEach(h -> timeline.add(AsCaseDto.HistoryItemDto.done(h)));
            return timeline;
        }

        List<AsStatus> doneStatuses = done.stream().map(AsStatusHistory::getStatus).toList();
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

        // 픽업 예약 생성과 같은 AS 행을 잠근다.
        // 락이 없으면 취소와 예약이 동시에 들어와 취소된 접수에 픽업이 붙을 수 있다.
        AsCase c = getOwnedForUpdate(memberId, asNo);

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

    /**
     * 상태를 바꾸는 명령용 — AS 행을 잠근 뒤 반환한다.
     * 동시 요청이 같은 상태를 읽고 둘 다 통과하는 것을 막는다.
     */
    public AsCase getOwnedForUpdate(Long memberId, String asNo) {
        AsCase c = asCaseRepository.findByAsNoForUpdate(asNo)
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
