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

    /** BCrypt 해시 */
    @Column(nullable = false, length = 60)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    /** 숫자만. INT 사용 시 선행 0 소실 + 상한 초과로 반드시 깨진다. */
    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** 이용약관 동의 (필수) */
    @Column(name = "agreed_service", nullable = false)
    private boolean agreedService;

    /** 개인정보 수집·이용 동의 (필수) */
    @Column(name = "agreed_privacy", nullable = false)
    private boolean agreedPrivacy;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Builder
    private Member(String email, String password, String name, String phone,
                   LocalDate birthDate, boolean agreedService, boolean agreedPrivacy) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.agreedService = agreedService;
        this.agreedPrivacy = agreedPrivacy;
        this.agreedAt = LocalDateTime.now();
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }
}
