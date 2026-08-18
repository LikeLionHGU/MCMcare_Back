package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.Handover;
import com.ladder.mcmcare.domain.Pickup;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class HandoverDto {

    /**
     * 719 인계 완료 확인.
     * 기사 정보는 handover 에 저장된 인계 시점 스냅샷이다.
     */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DetailResDto {
        private String asNo;
        private String modelName;
        private LocalDateTime handedOverAt;

        private List<String> photoUrlList;
        private String customerSignUrl;
        private String driverSignUrl;

        private String driverName;
        private String driverAffiliation;
        private String driverVerifyNo;

        private Boolean insuranceApplied;
        private Integer insuranceLimit;

        public static DetailResDto of(Handover h, List<String> photoUrls,
                                      String customerSignUrl, String driverSignUrl) {
            Pickup p = h.getPickup();
            return DetailResDto.builder()
                    .asNo(p.getAsCase().getAsNo())
                    .modelName(p.getAsCase().getModelName())
                    .handedOverAt(h.getHandedOverAt())
                    .photoUrlList(photoUrls)
                    .customerSignUrl(customerSignUrl)
                    .driverSignUrl(driverSignUrl)
                    .driverName(h.getDriverName())
                    .driverAffiliation(h.getDriverAffiliation())
                    .driverVerifyNo(h.getDriverVerifyNo())
                    .insuranceApplied(p.getInsuranceApplied())
                    .insuranceLimit(p.getInsuranceLimit())
                    .build();
        }
    }
}
