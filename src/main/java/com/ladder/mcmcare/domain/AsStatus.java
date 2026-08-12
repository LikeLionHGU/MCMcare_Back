package com.ladder.mcmcare.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * AS 진행 단계.
 *
 * label      화면 표기. 목록 뱃지·상세 단계에 그대로 쓰인다.
 * terminal   종료 상태 여부. 목록 필터·집계 판정에 쓰이므로 DB 컬럼이 필요 없다.
 * pending    아직 오지 않은 단계의 안내 문구. 상세 타임라인에서 "예정 · {문구}" 로 표시된다.
 */
@Getter
@RequiredArgsConstructor
public enum AsStatus {

    DRAFT           ("작성 중",                     false, null),
    ANALYZING       ("AI 분석 중",                  false, null),
    ESTIMATE_FAILED ("견적 실패",                   false, null),
    ESTIMATED       ("견적 안내 완료",               false, null),
    PICKUP_BOOKED   ("픽업 예약 완료 · 수거 대기 중", false, null),
    PICKED_UP       ("수거 완료",                   false, "기사 인계 후 수선 센터로 이동"),
    RECEIVED        ("접수 완료",                   false, "수선 센터 입고 및 접수 처리"),
    DIAGNOSED       ("진단 및 견적 확정",            false, "실물 진단 후 수선 범위 확정"),
    REPAIRING       ("수선 진행 중",                false, "확정된 범위로 수선 작업 진행"),
    INSPECTING      ("품질 검수",                   false, "수선 완료 후 품질 기준 최종 점검"),
    SHIPPING        ("반환 배송",                   false, "검수 완료 후 고객 배송 진행"),
    COMPLETED       ("완료",                       true,  null),
    CANCELLED       ("접수 취소",                   true,  null);

    private final String label;
    private final boolean terminal;
    private final String pendingDescription;

    /**
     * 관리자 API 가 진행시킬 수 있는 순서.
     * 임의 상태로 건너뛰거나 되돌리는 것을 막는다.
     */
    private static final List<AsStatus> PROGRESSION = List.of(
            PICKED_UP, RECEIVED, DIAGNOSED, REPAIRING, INSPECTING, SHIPPING, COMPLETED);

    /**
     * 관리자 전이 허용 여부.
     * 진행 순서상 뒤에 있는 단계로만 이동할 수 있다.
     * 되돌리기가 필요하면 별도 정책을 정한 뒤 열어야 한다.
     */
    public boolean canProgressTo(AsStatus next) {
        int from = PROGRESSION.indexOf(this);
        int to = PROGRESSION.indexOf(next);
        if (to < 0) return false;                 // 관리자가 옮길 수 없는 상태
        if (this == PICKUP_BOOKED) return next == PICKED_UP;   // 기사 인계 구간
        if (from < 0) return false;               // 아직 수거 전
        return to > from;
    }

    /** 상세 화면 타임라인에 노출되는 단계 (수거 이후 수선 센터 구간) */
    public static final List<AsStatus> TIMELINE = List.of(
            PICKED_UP, RECEIVED, DIAGNOSED, REPAIRING, INSPECTING, SHIPPING, COMPLETED);

    /** 목록에서 제외되는 상태 */
    public boolean isHidden() {
        return this == CANCELLED;
    }

    /** filter=IN_PROGRESS 대상 */
    public boolean isInProgress() {
        return !terminal;
    }

    public static List<AsStatus> inProgressValues() {
        return Arrays.stream(values()).filter(AsStatus::isInProgress).toList();
    }
}
