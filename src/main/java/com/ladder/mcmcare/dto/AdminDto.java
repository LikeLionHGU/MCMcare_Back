package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

public class AdminDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateStatusReqDto {

        @NotNull(message = "변경할 상태를 지정해 주세요.")
        private AsStatus status;

        /** 이력 타임라인에 남는 문구 */
        @Size(max = 200, message = "200자 이내로 입력해 주세요.")   // as_status_history.description
        private String description;

        /** 상세 화면 "최신 상태 메시지" */
        @Size(max = 200, message = "200자 이내로 입력해 주세요.")   // as_case.status_message
        private String statusMessage;

        private LocalDate expectedCompletedAt;
        @Size(max = 200, message = "200자 이내로 입력해 주세요.")   // as_case.delay_reason
        private String delayReason;

        @Size(max = 50, message = "50자 이내로 입력해 주세요.")   // as_case.current_location
        private String currentLocation;
        @Size(max = 20, message = "20자 이내로 입력해 주세요.")   // as_case.location_type
        private String locationType;
        @Size(max = 30, message = "30자 이내로 입력해 주세요.")   // as_case.location_status
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
