package com.ladder.mcmcare.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 손상 사진. 접수당 최대 3장.
 * 바이너리를 DB 에 넣지 않는다 — AI 추론에 이미지를 전달해야 하는 구조에서 부적합하다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "as_photo",
        indexes = @Index(name = "idx_photo_as", columnList = "as_id, sort_order")
)
public class AsPhoto extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "as_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_photo_as"))
    private AsCase asCase;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private PhotoType photoType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private AsPhoto(AsCase asCase, String fileUrl, PhotoType photoType, int sortOrder) {
        this.asCase = asCase;
        this.fileUrl = fileUrl;
        this.photoType = photoType;
        this.sortOrder = sortOrder;
    }

    public static AsPhoto of(AsCase asCase, String fileUrl, PhotoType photoType, int sortOrder) {
        return new AsPhoto(asCase, fileUrl, photoType, sortOrder);
    }
}
