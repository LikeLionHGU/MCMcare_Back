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
}
