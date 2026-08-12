package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PurchaseChannel {

    OFFICIAL_STORE("MCM 공식 매장"),
    DEPARTMENT_STORE("백화점"),
    DUTY_FREE("면세점"),
    ONLINE_STORE("온라인스토어"),
    ETC("기타");

    private final String label;
}
