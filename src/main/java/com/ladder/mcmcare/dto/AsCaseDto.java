package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.AsStatusHistory;
import com.ladder.mcmcare.domain.DamageType;
import com.ladder.mcmcare.domain.PhotoType;
import com.ladder.mcmcare.domain.Product;
import com.ladder.mcmcare.domain.ProductType;
import com.ladder.mcmcare.domain.PurchaseChannel;
import com.ladder.mcmcare.service.WarrantyEvaluator;
import com.ladder.mcmcare.service.port.EstimateResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AsCaseDto {

    // ── 3-1. Form (715 드롭다운) ─────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FormResDto {
        private List<CodeDto> productTypeList;
        private List<CodeDto> purchaseChannelList;
        private List<CodeDto> damageTypeList;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CodeDto {
        private String code;
        private String label;

        public static CodeDto of(String code, String label) {
            return CodeDto.builder().code(code).label(label).build();
        }
    }

    // ── 3-2. Create (715 예상 견적 확인하기) ─────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateReqDto {

        /** 필수입력 아님 */
        private String warrantyNo;

        @NotNull(message = "제품 종류를 선택해 주세요.")
        private ProductType productType;

        @NotBlank(message = "제품 모델명을 입력해 주세요.")
        @Size(max = 50, message = "제품 모델명은 50자 이내여야 합니다.")
        private String modelName;

        private LocalDate purchasedAt;

        private PurchaseChannel purchaseChannel;

        @NotBlank(message = "손상 부위를 입력해 주세요.")
        @Size(max = 50, message = "손상 부위는 50자 이내여야 합니다.")
        private String damagePart;

        @NotNull(message = "손상 유형을 선택해 주세요.")
        private DamageType damageType;

        @Size(max = 200, message = "손상 설명은 200자 이내여야 합니다.")
        private String damageDescription;

        /**
         * images 순서와 1:1 대응.
         * 화면에 종류 선택 UI 가 없으므로 프론트는 첫 장을 PRODUCT, 나머지를 DAMAGE 로 채운다.
         */
        @Size(min = 1, max = 4, message = "사진은 1~4장까지 첨부할 수 있습니다.")
        private List<@NotNull(message = "사진 종류가 지정되지 않았습니다.") PhotoType> photoTypeList;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateResDto {
        private String asNo;

        public static CreateResDto from(AsCase c) {
            return CreateResDto.builder().asNo(c.getAsNo()).build();
        }
    }

    // ── 3-3. Estimate (712 AI 예상 견적 결과) ────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EstimateResDto {
        private String asNo;
        private String status;
        private String statusLabel;

        private String modelName;
        private String damagePart;
        private List<String> photoUrlList;

        private String damageCategory;
        private String damageSeverity;
        private String confidenceGrade;
        private String confidenceNote;

        private List<EstimateItemDto> itemList;

        /** 항목별 추정 금액 합계 — 화면의 "예상 합계 약 N원" */
        private int totalEstimatedPrice;

        private int totalMinPrice;
        private int totalMaxPrice;

        /**
         * AI 가 손상을 탐지하지 못한 경우의 안내 문구.
         * 값이 있으면 화면은 비용 영역 대신 이 문구를 표시한다.
         */
        private String noDamageNotice;

        private LocalDate purchasedAt;
        private Integer warrantyMonths;
        private String warrantyScope;
        private String warrantyVerdict;
        private String warrantyVerdictLabel;
        private List<String> warrantyNoteList;

        public static EstimateResDto of(AsCase c, List<String> photoUrls,
                                        EstimateResult result, WarrantyEvaluator.Verdict verdict) {
            Product p = c.getProduct();
            return EstimateResDto.builder()
                    .asNo(c.getAsNo())
                    .status(c.getStatus().name())
                    .statusLabel(c.getStatus().getLabel())
                    .modelName(c.getModelName())
                    .damagePart(c.getDamagePart())
                    .photoUrlList(photoUrls)
                    .damageCategory(result.getDamageCategory())
                    .damageSeverity(result.getDamageSeverity())
                    .confidenceGrade(result.getConfidenceGrade())
                    .confidenceNote(result.getConfidenceNote())
                    .itemList(result.getItems().stream().map(EstimateItemDto::from).toList())
                    .totalEstimatedPrice(result.totalEstimatedPrice())
                    .totalMinPrice(result.totalMinPrice())
                    .totalMaxPrice(result.totalMaxPrice())
                    .noDamageNotice(result.getNoDamageNotice())
                    .purchasedAt(c.getPurchasedAt())
                    .warrantyMonths(p == null ? null : p.getWarrantyMonths())
                    .warrantyScope(p == null ? null : p.getWarrantyScope())
                    .warrantyVerdict(verdict.getCode())
                    .warrantyVerdictLabel(verdict.getLabel())
                    .warrantyNoteList(verdict.getNotes())
                    .build();
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EstimateItemDto {
        private String repairItemName;
        /** 비용 근거 수준 — 확인됨 / 부분확인 / 가설. 실측 단가가 없으면 "가설"이다. */
        private String costConfidence;

        /** 손상 정도가 반영된 추정 금액. 화면의 "약 N원" */
        private int estimatedPrice;

        /** 카테고리 고정 구간 — 손상 정도와 무관 */
        private int minPrice;
        private int maxPrice;

        public static EstimateItemDto from(EstimateResult.Item i) {
            return EstimateItemDto.builder()
                    .repairItemName(i.getRepairItemName())
                    .estimatedPrice(i.getEstimatedPrice())
                    .minPrice(i.getMinPrice())
                    .maxPrice(i.getMaxPrice())
                    .costConfidence(i.getCostConfidence())
                    .build();
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RetryResDto {
        private String asNo;
        private String status;

        public static RetryResDto from(AsCase c) {
            return RetryResDto.builder()
                    .asNo(c.getAsNo())
                    .status(c.getStatus().name())
                    .build();
        }
    }

    // ── 3-5. List (710 나의 AS 목록) ─────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ListReqDto {
        /** ALL · IN_PROGRESS · COMPLETED */
        private String filter;
        private Integer page;
        private Integer size;

        public String filterOrDefault() {
            return (filter == null || filter.isBlank()) ? "ALL" : filter;
        }
        public int pageOrDefault() {
            // 음수를 그대로 넘기면 PageRequest.of() 가 IllegalArgumentException 을 던져 500 이 된다.
            return (page == null || page < 0) ? 0 : page;
        }
        public int sizeOrDefault() {
            return (size == null || size < 1) ? 20 : Math.min(size, 100);
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ListResDto {
        private long inProgressCount;
        private long completedCount;
        private LocalDate lastUpdatedAt;
        private List<ListItemDto> itemList;
        private int totalPages;
        private long totalElements;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ListItemDto {
        private String asNo;
        private String modelName;

        /** 목록 썸네일 — 접수 시 올린 첫 번째 사진. 서명된 URL 이다. */
        private String thumbnailUrl;
        private LocalDate createdAt;
        private String status;
        private String statusLabel;
        private LocalDate expectedCompletedAt;
        private LocalDate completedAt;
        private LocalDateTime statusUpdatedAt;

        public static ListItemDto of(AsCase c, String thumbnailUrl) {
            return ListItemDto.builder()
                    .asNo(c.getAsNo())
                    .modelName(c.getModelName())
                    .thumbnailUrl(thumbnailUrl)
                    .createdAt(c.getCreatedAt().toLocalDate())
                    .status(c.getStatus().name())
                    .statusLabel(c.getStatus().getLabel())
                    .expectedCompletedAt(c.getExpectedCompletedAt())
                    .completedAt(c.getCompletedAt())
                    .statusUpdatedAt(c.getStatusUpdatedAt())
                    .build();
        }
    }

    // ── 3-6. Detail (716 나의 AS 상세) ───────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DetailResDto {
        private String asNo;
        private String modelName;
        private LocalDate createdAt;
        private String intakeType;
        private String pickupNo;

        /**
         * 접수 시 올린 사진. 서명된 URL 이다.
         * 목록은 첫 장(thumbnailUrl)만 쓰지만 상세는 전체를 보여줄 수 있어 리스트로 준다.
         */
        private List<String> photoUrlList;

        /**
         * 접수 시 사용자가 입력한 손상 정보.
         * AI 가 판정한 damageCategory(견적 응답)와는 다르다 — 이쪽은 고객의 진술이다.
         * AI 상담이 "어디가 어떻게 손상됐나요?" 에 답하려면 필요하다.
         */
        private String damagePart;
        private String damageType;
        private String damageTypeLabel;
        private String damageDescription;

        /**
         * AI 가 판정한 손상 유형. 견적이 없으면 null.
         * 고객이 고른 damageType 과 다를 수 있다 — AI 는 사진을 보고 판단한다.
         */
        private String damageCategory;

        private String status;
        private String statusLabel;
        private LocalDateTime statusUpdatedAt;
        private String statusMessage;

        private LocalDate expectedCompletedAt;
        private LocalDate expectedUpdatedAt;
        private String delayReason;

        private String currentLocation;
        private String locationType;
        private String locationStatus;

        private List<HistoryItemDto> historyList;
    }

    /**
     * 716 타임라인은 발생한 이력과 예정 단계를 함께 보여준다.
     * completed = false 인 항목은 화면에서 "예정 · {description}" 으로 표시된다.
     */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HistoryItemDto {
        private String status;
        private String statusLabel;
        private boolean completed;
        private LocalDate occurredAt;
        private String description;

        public static HistoryItemDto done(AsStatusHistory h) {
            return HistoryItemDto.builder()
                    .status(h.getStatus().name())
                    .statusLabel(h.getStatus().getLabel())
                    .completed(true)
                    .occurredAt(h.getOccurredAt().toLocalDate())
                    .description(h.getDescription())
                    .build();
        }

        public static HistoryItemDto pending(AsStatus s) {
            return HistoryItemDto.builder()
                    .status(s.name())
                    .statusLabel(s.getLabel())
                    .completed(false)
                    .occurredAt(null)
                    .description(s.getPendingDescription())
                    .build();
        }
    }

    // ── 3-8. Delete ──────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeleteResDto {
        private String asNo;

        public static DeleteResDto from(AsCase c) {
            return DeleteResDto.builder().asNo(c.getAsNo()).build();
        }
    }
}
