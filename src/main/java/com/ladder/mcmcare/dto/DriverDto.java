package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.Driver;
import com.ladder.mcmcare.domain.Pickup;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class DriverDto {

    // ── 5-1. Login ───────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginReqDto {

        @NotBlank(message = "아이디를 입력해 주세요.")
        private String loginId;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        private String password;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginResDto {
        private String accessToken;
        private long expiresIn;
        private Long driverId;
        private String name;

        public static LoginResDto of(Driver d, String token, long expiresIn) {
            return LoginResDto.builder()
                    .accessToken(token)
                    .expiresIn(expiresIn)
                    .driverId(d.getId())
                    .name(d.getName())
                    .build();
        }
    }

    // ── 5-2. PickupList (오늘의 수거 목록) ───────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PickupListResDto {
        private LocalDate pickupDate;
        private List<PickupItemDto> itemList;
    }

    /**
     * 인계에 필요한 정보를 모두 담는다.
     * 별도 상세 조회 API 를 두지 않는 이유다.
     */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PickupItemDto {
        private String pickupNo;
        private LocalTime slotStart;
        private LocalTime slotEnd;

        private String customerName;
        private String phone;
        private String address;
        private String addressDetail;
        private String note;

        private String modelName;
        private String damagePart;
        private List<String> asPhotoUrlList;

        public static PickupItemDto of(Pickup p, List<String> asPhotoUrls) {
            AsCase c = p.getAsCase();
            return PickupItemDto.builder()
                    .pickupNo(p.getPickupNo())
                    .slotStart(p.getSlotStart())
                    .slotEnd(p.getSlotEnd())
                    .customerName(c.getMember().getName())
                    .phone(p.getPhone())
                    .address(p.getAddress())
                    .addressDetail(p.getAddressDetail())
                    .note(p.getNote())
                    .modelName(c.getModelName())
                    .damagePart(c.getDamagePart())
                    .asPhotoUrlList(asPhotoUrls)
                    .build();
        }
    }

    // ── 5-3. Handover (인계 완료) ────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HandoverResDto {
        private String pickupNo;
        private LocalDateTime handedOverAt;
    }
}
