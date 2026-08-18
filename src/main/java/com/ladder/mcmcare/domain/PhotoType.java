package com.ladder.mcmcare.domain;

import java.util.List;

/**
 * 접수 사진 종류.
 *
 * 화면은 "전체 제품 사진 1장과 손상 부위 사진을 최소 1장 이상" 을 요구한다.
 * 사진마다 종류를 고르는 UI 는 없고 순서로 구분하므로,
 * 프론트는 첫 장을 PRODUCT, 나머지를 DAMAGE 로 채워 보낸다.
 */
public enum PhotoType {

    /** 제품 전체 사진 — AI 가 가방 영역을 잡아 손상 면적비를 계산하는 기준이 된다 */
    PRODUCT,

    /** 손상 부위 사진 */
    DAMAGE;

    /** 접수당 사진 최대 장수 */
    public static final int MAX_COUNT = 4;

    /**
     * 화면 요구를 만족하는 조합인지 확인한다.
     * 전체 사진이 없으면 AI 가 면적비를 계산하지 못해 심각도 판정이 부정확해진다.
     */
    public static boolean isValidCombination(List<PhotoType> types) {
        if (types == null || types.isEmpty()) return false;
        return types.contains(PRODUCT) && types.contains(DAMAGE);
    }
}
