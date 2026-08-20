package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email")
)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    /** 아이디 겸용 */
    @Column(nullable = false, length = 50)
    private String email;

    /**
     * BCrypt 해시.
     * 소셜 가입 회원은 비밀번호가 없으므로 null 이다 —
     * 빈 문자열이나 임의값을 넣으면 그 값으로 로그인이 시도될 여지가 생긴다.
     */
    @Column(length = 60)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    /**
     * 숫자만. INT 사용 시 선행 0 소실 + 상한 초과로 반드시 깨진다.
     * 소셜 가입 회원은 구글이 연락처를 주지 않아 null 이다.
     */
    @Column(length = 50)
    private String phone;

    /** 소셜 가입 회원은 null */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 이용약관 동의 (필수) */
    @Column(name = "agreed_service", nullable = false)
    private boolean agreedService;

    /** 개인정보 수집·이용 동의 (필수) */
    @Column(name = "agreed_privacy", nullable = false)
    private boolean agreedPrivacy;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    /** 가입 경로 — 소셜 회원은 연락처·생년월일이 비어 있다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /**
     * 소프트 삭제 시각. null 이면 정상 계정, 값이 있으면 탈퇴 계정.
     * 하드 삭제를 하지 않는 이유: AS 접수 건이 member_id 를 외래키로 참조하고 있어서
     * 삭제하면 FK 제약 위반이 발생한다. 이력·통계 보존을 위해 행을 남긴다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Member(String email, String password, String name, String phone,
                   LocalDate birthDate, boolean agreedService, boolean agreedPrivacy,
                   AuthProvider provider) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.agreedService = agreedService;
        this.agreedPrivacy = agreedPrivacy;
        this.agreedAt = LocalDateTime.now();
        this.provider = (provider == null) ? AuthProvider.LOCAL : provider;
    }

    /**
     * 구글 계정으로 가입.
     *
     * 구글이 주는 것은 이메일과 이름뿐이다.
     * 필수 약관은 로그인 시점에 동의한 것으로 처리한다 —
     * 프론트가 구글 버튼 옆에 약관 링크를 노출하는 전제다.
     */
    public static Member ofGoogle(String email, String name) {
        return Member.builder()
                .email(email)
                .name(name)
                .agreedService(true)
                .agreedPrivacy(true)
                .provider(AuthProvider.GOOGLE)
                .build();
    }

    /** 소셜 회원이 아직 연락처를 입력하지 않았는지 */
    public boolean needsContactInfo() {
        return phone == null || phone.isBlank();
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    /**
     * 회원 탈퇴 — 소프트 삭제 + 개인정보 익명화.
     *
     * email 을 그냥 지우면 UNIQUE 제약상 빈 값이 중복될 수 있다.
     * member_id 를 포함한 고유값으로 덮어써서 제약 위반 없이 익명화한다.
     * 나머지 PII(이름·연락처·생년월일·비밀번호)는 복구 불가 수준으로 지운다.
     */
    public void withdraw() {
        this.email = "withdrawn_" + this.id + "@deleted.local";
        this.name = "탈퇴한 회원";
        this.phone = null;
        this.birthDate = null;
        this.password = null;
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 비밀번호 변경. 인코딩은 서비스 레이어에서 수행하고 결과만 받는다.
     * 엔티티가 PasswordEncoder 를 알 필요가 없도록 이미 해시된 값을 받는다.
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 탈퇴 여부 */
    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }
}
