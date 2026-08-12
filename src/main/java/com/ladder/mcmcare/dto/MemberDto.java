package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.Member;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class MemberDto {

    // ── 1-1. Signup ──────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SignupReqDto {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = 50, message = "이메일은 50자 이내여야 합니다.")
        private String email;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 50, message = "비밀번호는 50자 이내여야 합니다.")
        private String password;

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        private String passwordConfirm;

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 30, message = "이름은 30자 이내여야 합니다.")
        private String name;

        @NotBlank(message = "연락처를 입력해 주세요.")
        @Pattern(regexp = "\\d+", message = "연락처는 숫자만 입력해 주세요.")
        @Size(max = 50, message = "연락처는 50자 이내여야 합니다.")
        private String phone;

        @NotNull(message = "생년월일을 입력해 주세요.")
        private LocalDate birthDate;

        private boolean agreedService;
        private boolean agreedPrivacy;
        private boolean agreedMarketing;

        public Member toEntity(String encodedPassword) {
            return Member.builder()
                    .email(email)
                    .password(encodedPassword)
                    .name(name)
                    .phone(phone)
                    .birthDate(birthDate)
                    .agreedService(agreedService)
                    .agreedPrivacy(agreedPrivacy)
                    .build();
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SignupResDto {
        private Long memberId;

        public static SignupResDto from(Member member) {
            return SignupResDto.builder().memberId(member.getId()).build();
        }
    }

    // ── 1-2. Login ───────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginReqDto {

        @NotBlank(message = "이메일을 입력해 주세요.")
        private String email;

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        private String password;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginResDto {
        private String accessToken;
        private long expiresIn;
        private Long memberId;
        private String name;

        public static LoginResDto of(Member member, String accessToken, long expiresIn) {
            return LoginResDto.builder()
                    .accessToken(accessToken)
                    .expiresIn(expiresIn)
                    .memberId(member.getId())
                    .name(member.getName())
                    .build();
        }
    }

    // ── 1-3. Home (714 홈 화면) ──────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HomeResDto {
        private List<HomeAsCaseDto> asCaseList;

        public static HomeResDto from(List<AsCase> cases) {
            return HomeResDto.builder()
                    .asCaseList(cases.stream().map(HomeAsCaseDto::from).toList())
                    .build();
        }
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HomeAsCaseDto {
        private String asNo;
        private String modelName;
        private String status;
        private String statusLabel;
        private LocalDate expectedCompletedAt;
        private LocalDate completedAt;

        public static HomeAsCaseDto from(AsCase c) {
            return HomeAsCaseDto.builder()
                    .asNo(c.getAsNo())
                    .modelName(c.getModelName())
                    .status(c.getStatus().name())
                    .statusLabel(c.getStatus().getLabel())
                    .expectedCompletedAt(c.getExpectedCompletedAt())
                    .completedAt(c.getCompletedAt())
                    .build();
        }
    }

    // ── 1-4. Info ────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InfoResDto {
        private Long memberId;
        private String email;
        private String name;
        private String phone;
        private LocalDate birthDate;
        private boolean agreedMarketing;

        public static InfoResDto of(Member member, boolean agreedMarketing) {
            return InfoResDto.builder()
                    .memberId(member.getId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .phone(member.getPhone())
                    .birthDate(member.getBirthDate())
                    .agreedMarketing(agreedMarketing)
                    .build();
        }
    }

    // ── 1-5. Update ──────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateReqDto {

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 30, message = "이름은 30자 이내여야 합니다.")
        private String name;

        @NotBlank(message = "연락처를 입력해 주세요.")
        @Pattern(regexp = "\\d+", message = "연락처는 숫자만 입력해 주세요.")
        @Size(max = 50, message = "연락처는 50자 이내여야 합니다.")
        private String phone;

        private boolean agreedMarketing;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateResDto {
        private Long memberId;

        public static UpdateResDto from(Member member) {
            return UpdateResDto.builder().memberId(member.getId()).build();
        }
    }
}
