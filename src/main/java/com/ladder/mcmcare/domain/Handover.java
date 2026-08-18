package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 인계 기록. pickup 과 1:1.
 *
 * 기사 정보를 스냅샷으로 복사하는 이유:
 * 기사가 퇴사하거나 차량 번호가 바뀌어도 인계 시점 기록은 변하지 않아야 한다.
 * 분쟁 시 참고 자료로 쓰이므로 당시 값이 보존돼야 한다.
 *
 * handed_over_at 도 같은 이유로 클라이언트가 보내지 않고 서버가 기록한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "handover",
        uniqueConstraints = @UniqueConstraint(name = "uk_handover_pickup", columnNames = "pickup_id"),
        indexes = @Index(name = "idx_handover_driver", columnList = "driver_id")
)
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "handover_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_handover_pickup"))
    private Pickup pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_handover_driver"))
    private Driver driver;

    // ── 기사 정보 스냅샷 ──
    @Column(name = "driver_name", nullable = false, length = 30)
    private String driverName;

    @Column(name = "driver_phone", nullable = false, length = 50)
    private String driverPhone;

    @Column(name = "driver_vehicle_no", length = 20)
    private String driverVehicleNo;

    @Column(name = "driver_verify_no", length = 30)
    private String driverVerifyNo;

    @Column(name = "driver_affiliation", length = 50)
    private String driverAffiliation;

    @Column(name = "customer_sign_url", nullable = false, length = 500)
    private String customerSignUrl;

    @Column(name = "driver_sign_url", nullable = false, length = 500)
    private String driverSignUrl;

    @Column(name = "handed_over_at", nullable = false)
    private LocalDateTime handedOverAt;

    @OneToMany(mappedBy = "handover", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<HandoverPhoto> photos = new ArrayList<>();

    private Handover(Pickup pickup, Driver driver, String customerSignUrl, String driverSignUrl) {
        this.pickup = pickup;
        this.driver = driver;
        this.driverName = driver.getName();
        this.driverPhone = driver.getPhone();
        this.driverVehicleNo = driver.getVehicleNo();
        this.driverVerifyNo = driver.getVerifyNo();
        this.driverAffiliation = driver.getAffiliation();
        this.customerSignUrl = customerSignUrl;
        this.driverSignUrl = driverSignUrl;
        this.handedOverAt = LocalDateTime.now();
    }

    public static Handover of(Pickup pickup, Driver driver,
                              String customerSignUrl, String driverSignUrl) {
        return new Handover(pickup, driver, customerSignUrl, driverSignUrl);
    }

    public void addPhoto(HandoverPhoto photo) {
        this.photos.add(photo);
    }
}
