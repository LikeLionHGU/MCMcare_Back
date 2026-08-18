package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 제품 정보 입력 화면 드롭다운. 항목 추가 시 여기 한 줄만 추가하면 된다. */
@Getter
@RequiredArgsConstructor
public enum ProductType {

    BAG("가방"),
    WALLET("지갑"),
    BELT("벨트"),
    SHOES("신발"),
    ACCESSORY("소품"),
    ETC("기타");

    private final String label;
}
