package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.DamageType;
import com.ladder.mcmcare.domain.Product;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 보증 적용 판정.
 *
 * AI 와 무관하다 — 구매일 · 보증기간 · 손상 유형만으로 결정되므로
 * 견적 구현체가 스텁이든 실제 모델이든 그대로 재사용된다.
 */
@Component
public class WarrantyEvaluator {

    /** 제조 결함으로 인정될 여지가 있는 손상 유형 */
    private static final Set<DamageType> DEFECT_CANDIDATES =
            Set.of(DamageType.STITCHING, DamageType.METAL_PART);

    @Getter
    @Builder
    public static class Verdict {
        private String code;        // COVERED / PARTIAL / NOT_COVERED / UNKNOWN
        private String label;
        private List<String> notes;
    }

    public Verdict evaluate(AsCase asCase) {

        Product product = asCase.getProduct();

        // 보증서 없이 접수한 경우
        if (product == null) {
            return Verdict.builder()
                    .code("UNKNOWN")
                    .label("보증 확인 불가")
                    .notes(List.of("보증서 번호가 없어 보증 적용 여부를 확인할 수 없습니다. "
                            + "구매 영수증 또는 주문번호가 있으면 입고 후 확인 가능합니다."))
                    .build();
        }

        LocalDate expires = product.getWarrantyExpiresAt();
        List<String> notes = new ArrayList<>();

        // 만료일을 계산할 수 없는 경우 — 구매일이 확인되지 않는 병행수입 등.
        // "만료"로 단정하면 무상 대상일 수도 있는 건을 유상으로 안내하게 된다.
        if (expires == null) {
            notes.add("구매일이 확인되지 않아 보증 기간을 산정할 수 없습니다.");
            notes.add("구매 영수증 또는 주문번호가 있으면 입고 후 확인 가능합니다.");
            return Verdict.builder()
                    .code("UNKNOWN")
                    .label("보증 확인 불가")
                    .notes(notes)
                    .build();
        }

        boolean defectCandidate = DEFECT_CANDIDATES.contains(asCase.getDamageType());

        if (expires.isBefore(LocalDate.now())) {
            notes.add("보증 기간이 만료되어 유상 수선으로 진행됩니다.");
            return Verdict.builder().code("NOT_COVERED").label("보증 기간 만료").notes(notes).build();
        }

        if (!defectCandidate) {
            notes.add("정상 사용에 따른 소모로 분류될 경우 보증 적용이 제한될 수 있습니다.");
            return Verdict.builder().code("NOT_COVERED").label("보증 적용 어려움").notes(notes).build();
        }

        notes.add("해당 손상이 제조 결함으로 확인되면 무상 수선이 적용될 수 있습니다.");
        notes.add("정상 사용에 따른 소모로 분류될 경우 보증 적용이 제한될 수 있습니다.");
        notes.add("보증 적용 여부는 입고 후 담당 수선 전문가의 실물 진단에서 최종 확정됩니다.");

        return Verdict.builder()
                .code("PARTIAL")
                .label("부분 보증 적용 가능성 있음")
                .notes(notes)
                .build();
    }
}
