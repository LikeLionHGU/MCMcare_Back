package com.ladder.mcmcare.service.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * AI 진단 서버(FastAPI) 의 POST /diagnose/multi 응답.
 *
 * 서버가 필드를 추가해도 깨지지 않도록 @JsonIgnoreProperties 를 둔다.
 * 금액 단위는 EUR 이며, 원화 환산은 EstimateMapper 가 담당한다.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiDiagnoseResponse {

    @JsonProperty("n_images")
    private int nImages;

    private List<Damage> damages;

    @JsonProperty("overall_severity")
    private String overallSeverity;      // 경미 | 보통 | 심각 | null

    @JsonProperty("total_estimated_cost_eur")
    private Cost totalEstimatedCostEur;  // 손상 없으면 null

    private List<String> warnings;

    @JsonProperty("model_version")
    private String modelVersion;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Damage {

        @JsonProperty("mcm_category")
        private String mcmCategory;      // 찢김/파열 · 균열/파손 · 변형 · 손상(세부미상)

        @JsonProperty("detection_confidence")
        private double detectionConfidence;

        private String severity;         // 경미 | 보통 | 심각

        @JsonProperty("area_ratio")
        private double areaRatio;

        @JsonProperty("bag_box_detected")
        private boolean bagBoxDetected;

        @JsonProperty("estimated_cost_eur")
        private Cost estimatedCostEur;

        @JsonProperty("cost_confidence_tag")
        private String costConfidenceTag; // 확인됨 | 부분확인 | 가설

        /** 여러 사진 중 이 카테고리의 대표값으로 채택된 사진명 (화면에는 쓰지 않고 로그·디버깅용) */
        @JsonProperty("source_image")
        private String sourceImage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cost {
        private double min;
        private double max;

        @JsonProperty("point_estimate")
        private double pointEstimate;
    }
}
