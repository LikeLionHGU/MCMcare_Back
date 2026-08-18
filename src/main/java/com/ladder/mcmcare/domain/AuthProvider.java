package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회원 가입 경로.
 *
 * 소셜 가입은 구글이 주는 정보(이메일 · 이름)만으로 계정을 만들기 때문에
 * 연락처 · 생년월일이 비어 있다. 픽업 예약처럼 연락처가 필요한 시점에
 * 추가 입력을 요구할지 판단하려면 가입 경로를 알아야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthProvider {

    LOCAL("이메일"),
    GOOGLE("구글");

    private final String label;
}
