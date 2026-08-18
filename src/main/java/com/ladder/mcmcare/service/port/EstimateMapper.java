package com.ladder.mcmcare.service.port;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 서버 응답 → 화면 표시값 변환 규칙.
 *
 * 통화 환산 · 반올림 · 등급화 규칙을 여기 한 곳에 모은다.
 * 스텁 어댑터와 실제 모델 어댑터가 같은 규칙을 쓰도록 하기 위함이다.
 */
@Component
public class EstimateMapper {

    /** 손상 정도 등급 → 화면 문구 */
    private static final String SEVERITY_MINOR    = "경미 — 부분 보수 가능 수준";
    private static final String SEVERITY_MODERATE = "중간 — 부분 수선 가능 수준";
    private static final String SEVERITY_SEVERE   = "심각 — 광범위 수선 필요";

    /** 신뢰도 등급 경계 */
    private static final double CONFIDENCE_HIGH = 0.7;
    private static final double CONFIDENCE_MID  = 0.4;

    /** 반올림 단위 — 천원 */
    private static final int ROUND_UNIT = 1_000;

    public static final String NO_DAMAGE_NOTICE =
            "사진에서 손상이 뚜렷하게 식별되지 않았습니다. "
            + "다른 각도의 사진을 추가하거나, 입고 후 실물 진단에서 확인해 주세요.";

    private final int eurToKrw;

    public EstimateMapper(@Value("${app.estimate.eur-to-krw}") int eurToKrw) {
        this.eurToKrw = eurToKrw;
    }

    /**
     * 최솟값 환산 — 천원 단위 내림.
     * 범위가 실제보다 좁아 보이지 않도록 min 은 내리고 max 는 올린다.
     */
    public int toKrwFloor(double eur) {
        long krw = Math.round(eur * eurToKrw);
        return (int) (krw / ROUND_UNIT * ROUND_UNIT);
    }

    /**
     * 추정 금액 환산 — 천원 단위 반올림.
     * min 처럼 내리거나 max 처럼 올릴 이유가 없는 중간값이므로 가장 가까운 단위로 맞춘다.
     */
    public int toKrwRound(double eur) {
        long krw = Math.round(eur * eurToKrw);
        return (int) (Math.round((double) krw / ROUND_UNIT) * ROUND_UNIT);
    }

    /** 최댓값 환산 — 천원 단위 올림 */
    public int toKrwCeil(double eur) {
        long krw = Math.round(eur * eurToKrw);
        return (int) (((krw + ROUND_UNIT - 1) / ROUND_UNIT) * ROUND_UNIT);
    }

    /**
     * AI 의 severity 등급을 화면 문구로 바꾼다.
     * AI 는 "경미" / "보통" / "심각" 세 값만 반환한다.
     */
    public String severityLabel(String aiSeverity) {
        if (aiSeverity == null) return null;
        return switch (aiSeverity) {
            case "경미" -> SEVERITY_MINOR;
            case "보통" -> SEVERITY_MODERATE;
            case "심각" -> SEVERITY_SEVERE;
            default -> aiSeverity;   // 미정의 값이 오면 그대로 노출해 원인을 드러낸다
        };
    }

    /** detection_confidence 최댓값 → 등급 */
    public String confidenceGrade(double maxConfidence) {
        if (maxConfidence >= CONFIDENCE_HIGH) return "높음";
        if (maxConfidence >= CONFIDENCE_MID)  return "보통";
        return "낮음";
    }

    public String confidenceNote(int photoCount) {
        return "제출 사진 %d장 기반".formatted(photoCount);
    }

    /**
     * 여러 손상 카테고리를 한 문장으로 합친다.
     * 712 의 "분류된 손상 유형" 자리는 한 줄이므로 목록을 이어 붙인다.
     */
    public String joinCategories(java.util.List<String> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return String.join(" · ", categories);
    }
}
