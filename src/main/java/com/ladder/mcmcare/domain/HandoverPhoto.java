package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인계 전 제품 상태 사진 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "handover_photo",
        indexes = @Index(name = "idx_handover_photo", columnList = "handover_id, sort_order")
)
public class HandoverPhoto extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hphoto_handover"))
    private Handover handover;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private HandoverPhoto(Handover handover, String fileUrl, int sortOrder) {
        this.handover = handover;
        this.fileUrl = fileUrl;
        this.sortOrder = sortOrder;
    }

    public static HandoverPhoto of(Handover handover, String fileUrl, int sortOrder) {
        return new HandoverPhoto(handover, fileUrl, sortOrder);
    }
}
