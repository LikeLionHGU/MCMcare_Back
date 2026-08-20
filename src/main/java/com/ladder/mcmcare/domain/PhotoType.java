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
     * 최소 1장이면 접수할 수 있다.
     *
     * [전에는 PRODUCT + DAMAGE 를 둘 다 요구했다]
     * 전체 사진이 있어야 AI 가 가방 영역을 잡아 손상 면적비를 계산할 수 있기 때문이다.
     * 없으면 이미지 전체를 가방으로 가정하므로 심각도와 금액이 부정확해진다.
     *
     * 그래도 제약을 푼 이유는 사용자가 손상 부위만 찍어 올리는 경우가 많아서다.
     * 대신 그 부정확함을 숨기지 않는다 — 가방 박스를 못 찾으면
     * EstimateMapper 가 신뢰도 등급을 한 단계 낮추고 안내 문구를 붙인다.
     */
    public static boolean isValidCombination(List<PhotoType> types) {
        return types != null && !types.isEmpty();
    }
}
