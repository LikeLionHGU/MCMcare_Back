package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PickupStatus {

    BOOKED("예약 완료"),
    CANCELLED("예약 취소"),
    COMPLETED("수거 완료");

    private final String label;

    /** 변경·취소는 BOOKED 에서만 가능하다. COMPLETED 는 되돌릴 수 없다. */
    public boolean isChangeable() {
        return this == BOOKED;
    }
}
