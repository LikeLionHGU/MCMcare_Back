package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DamageType {

    DENT("찍힘"),
    SCRATCH("긁힘"),
    DISCOLOR("변색"),
    METAL_PART("금속부품손상"),
    STITCHING("봉제손상"),
    ETC("기타");

    private final String label;
}
