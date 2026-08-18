package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AS 진행 이력.
 *
 * 컬럼 방식(received_at, repairing_at ...)을 쓰지 않는 이유:
 *   1. 검수 불합격 시 수선으로 되돌아가면 컬럼 하나로는 첫 기록이 덮인다
 *   2. 단계 추가가 ALTER TABLE 이 된다
 *   3. 단계별 설명 문구를 담을 수 없다
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "as_status_history",
        indexes = @Index(name = "idx_history_as", columnList = "as_id, occurred_at")
)
public class AsStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "as_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_history_as"))
    private AsCase asCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AsStatus status;

    @Column(length = 200)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private AsStatusHistory(AsCase asCase, AsStatus status, String description) {
        this.asCase = asCase;
        this.status = status;
        this.description = description;
        this.occurredAt = LocalDateTime.now();
    }

    public static AsStatusHistory of(AsCase asCase, AsStatus status, String description) {
        return new AsStatusHistory(asCase, status, description);
    }
}
