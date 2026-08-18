package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

public class AdminDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateStatusReqDto {

        @NotNull(message = "변경할 상태를 지정해 주세요.")
        private AsStatus status;

        /** 이력 타임라인에 남는 문구 */
        private String description;

        /** 상세 화면 "최신 상태 메시지" */
        private String statusMessage;

        private LocalDate expectedCompletedAt;
        private String delayReason;

        private String currentLocation;
        private String locationType;
        private String locationStatus;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateStatusResDto {
        private String asNo;
        private String status;

        public static UpdateStatusResDto from(AsCase c) {
            return UpdateStatusResDto.builder()
                    .asNo(c.getAsNo())
                    .status(c.getStatus().name())
                    .build();
        }
    }
}
