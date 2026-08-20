package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.Pickup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class PickupDto {

    // ── 4-1. Form (720 진입) ─────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FormResDto {
        private String asNo;
        private String modelName;
        private String statusLabel;
        /** 접수 대표 사진 1장. 서명된 URL 이다. */
        private String photoUrl;

        /**
         * 같은 사진을 배열로도 준다.
         * 견적 화면(EstimateResDto)이 photoUrlList 를 쓰므로 프론트가 같은 코드를 재사용할 수 있다.
         * 필드명이 달라 화면마다 다르게 읽어야 하면 실수가 생긴다.
         */
        private List<String> photoUrlList;
        private String phone;

        public static FormResDto of(AsCase c, String photoUrl, String phone) {
            return FormResDto.builder()
                    .asNo(c.getAsNo())
                    .modelName(c.getModelName())
                    .statusLabel(c.getStatus().getLabel())
                    .photoUrl(photoUrl)
                    .photoUrlList(photoUrl == null ? List.of() : List.of(photoUrl))
                    .phone(phone)
                    .build();
        }
    }

    // ── 4-2. Slot (720 예약 가능 슬롯) ───────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotReqDto {
        private LocalDate startDate;
        private LocalDate endDate;

        public LocalDate startOrToday() {
            return startDate == null ? LocalDate.now() : startDate;
        }
        public LocalDate endOrPlus14() {
            return endDate == null ? startOrToday().plusDays(14) : endDate;
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotResDto {
        private List<SlotDateDto> dateList;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotDateDto {
        private LocalDate date;
        private List<SlotItemDto> slotList;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotItemDto {
        private LocalTime slotStart;
        private LocalTime slotEnd;
        private boolean available;
    }

    // ── 4-3. Create (720 예약 확정) ──────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateReqDto {

        @NotNull(message = "픽업 날짜를 선택해 주세요.")
        private LocalDate pickupDate;

        @NotNull(message = "픽업 시간대를 선택해 주세요.")
        private LocalTime slotStart;

        @NotBlank(message = "연락처를 입력해 주세요.")
        @Pattern(regexp = "\\d+", message = "연락처는 숫자만 입력해 주세요.")
        @Size(max = 50)
        private String phone;

        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 100, message = "주소는 100자 이내여야 합니다.")
        private String address;

        @Size(max = 100, message = "상세 주소는 100자 이내여야 합니다.")
        private String addressDetail;

        @Size(max = 200, message = "전달 사항은 200자 이내여야 합니다.")
        private String note;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateResDto {
        private String pickupNo;

        public static CreateResDto from(Pickup p) {
            return CreateResDto.builder().pickupNo(p.getPickupNo()).build();
        }
    }

    // ── 4-4. Complete (713 예약 완료) ────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompleteResDto {
        private String pickupNo;
        private String asNo;
        private LocalDate pickupDate;
        private LocalTime slotStart;
        private LocalTime slotEnd;
        private String address;
        private String addressDetail;
        private String note;
        private String phone;
        private String status;
        private String driverName;
        private String driverAffiliation;
        private Boolean insuranceApplied;
        private Integer insuranceLimit;

        public static CompleteResDto from(Pickup p) {
            return CompleteResDto.builder()
                    .pickupNo(p.getPickupNo())
                    .asNo(p.getAsCase().getAsNo())
                    .pickupDate(p.getPickupDate())
                    .slotStart(p.getSlotStart())
                    .slotEnd(p.getSlotEnd())
                    .address(p.getAddress())
                    .addressDetail(p.getAddressDetail())
                    .note(p.getNote())
                    .phone(p.getPhone())
                    .status(p.getStatus().name())
                    .driverName(p.getDriver() == null ? null : p.getDriver().getName())
                    .driverAffiliation(p.getDriver() == null ? null : p.getDriver().getAffiliation())
                    .insuranceApplied(p.getInsuranceApplied())
                    .insuranceLimit(p.getInsuranceLimit())
                    .build();
        }
    }

    // ── 4-6. Cancel ──────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CancelResDto {
        private String pickupNo;

        public static CancelResDto from(Pickup p) {
            return CancelResDto.builder().pickupNo(p.getPickupNo()).build();
        }
    }
}
