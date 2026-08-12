package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수거 기사 계정.
 * 가입 화면이 없으므로 운영자가 시드로 생성한다.
 * 예약 확정 시 active = true 인 기사 중 자동 배정된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "driver",
        uniqueConstraints = @UniqueConstraint(name = "uk_driver_login", columnNames = "login_id")
)
public class Driver extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "driver_id")
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(nullable = false, length = 60)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    /** 신원 확인 번호 */
    @Column(name = "verify_no", length = 30)
    private String verifyNo;

    /** MCM 공식 파트너 수거 기사 */
    @Column(length = 50)
    private String affiliation;

    /** 배차 대상 여부 */
    @Column(nullable = false)
    private boolean active;

    @Builder
    private Driver(String loginId, String password, String name, String phone,
                   String vehicleNo, String verifyNo, String affiliation, boolean active) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.vehicleNo = vehicleNo;
        this.verifyNo = verifyNo;
        this.affiliation = affiliation;
        this.active = active;
    }
}
