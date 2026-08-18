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

    // 목록에 노출되지 않는 내부 상태
    DRAFT           ("작성 중",           false, null),
    ANALYZING       ("AI 분석 중",        false, null),
    ESTIMATE_FAILED ("견적 실패",         false, null),

    // 화면 라벨은 디자인 시안 기준이다.
    // 코드명(ESTIMATED 등)은 흐름상의 의미를, label 은 사용자가 보는 말을 담는다.
    ESTIMATED       ("접수중",            false, null),
    PICKUP_BOOKED   ("접수완료",          false, null),
    PICKED_UP       ("픽업완료",          false, "기사 인계 후 수선 센터로 이동"),
    RECEIVED        ("손상부위 진단중",    false, "수선 센터 입고 후 실물 진단"),
    DIAGNOSED       ("손상부위 진단완료",  false, "진단 결과에 따라 수선 범위 확정"),
    REPAIRING       ("수선중",            false, "확정된 범위로 수선 작업 진행"),
    INSPECTING      ("검수중",            false, "수선 완료 후 품질 기준 최종 점검"),
    SHIPPING        ("발송중",            false, "검수 완료 후 고객 배송 진행"),
    COMPLETED       ("완료",              true,  null),
    CANCELLED       ("접수 취소",         true,  null);

    private final String label;
    private final boolean terminal;
    private final String pendingDescription;

    /**
     * 수거 완료 이후의 진행 순서.
     * PICKED_UP 이 목록에 포함된 것은 "여기서 출발한다"는 뜻이지 관리자가 도달할 수 있다는 뜻이 아니다.
     */
    private static final List<AsStatus> PROGRESSION = List.of(
            PICKED_UP, RECEIVED, DIAGNOSED, REPAIRING, INSPECTING, SHIPPING, COMPLETED);

    /**
     * 관리자 API 가 이 상태로 전이시킬 수 있는지 판정한다.
     *
     * 규칙
     *   - PICKED_UP 은 기사 인계로만 도달한다.
     *     관리자가 직접 넘기면 handover 기록도 없고 pickup 은 BOOKED 인 채로
     *     AS 만 "수거 완료"가 되어 데이터가 어긋난다 (719 화면이 404 가 된다).
     *   - 수거 전(ESTIMATED · PICKUP_BOOKED)에서는 어떤 단계로도 갈 수 없다.
     *   - 앞으로 건너뛰기는 허용하되, 되돌리기와 제자리는 막는다.
     */
    public boolean canAdminProgressTo(AsStatus next) {

        // 인계는 기사(또는 자동 인계 스케줄러)만 수행한다
        if (next == PICKED_UP) return false;

        int from = PROGRESSION.indexOf(this);
        int to = PROGRESSION.indexOf(next);

        if (from < 0) return false;   // 아직 수거 전이거나 종료된 상태
        if (to < 0) return false;     // 관리자가 옮길 수 없는 상태

        return to > from;
    }

    /**
     * 견적 조회가 가능한 상태.
     *
     * DRAFT · ANALYZING 은 아직 분석이 끝나지 않았고,
     * ESTIMATE_FAILED 는 재분석 API 로 처리해야 한다.
     * 이 상태들에서 조회를 허용하면 조회만으로 AI 가 호출되어 비용이 발생한다.
     */
    public boolean isEstimateViewable() {
        return switch (this) {
            case DRAFT, ANALYZING, ESTIMATE_FAILED, CANCELLED -> false;
            default -> true;
        };
    }

    /** 상세 화면 타임라인에 노출되는 단계 (수거 이후 수선 센터 구간) */
    public static final List<AsStatus> TIMELINE = List.of(
            PICKED_UP, RECEIVED, DIAGNOSED, REPAIRING, INSPECTING, SHIPPING, COMPLETED);

    /**
     * 목록에서 제외되는 상태.
     *
     * DRAFT · ANALYZING 은 접수 생성과 AI 분석 사이의 중간 상태다.
     * 정상 흐름에서는 수 초 안에 ESTIMATED 또는 ESTIMATE_FAILED 로 바뀌므로
     * 사용자에게 노출할 이유가 없다.
     * 서버가 그 사이에 죽어 남은 건은 스케줄러가 ESTIMATE_FAILED 로 정리한다.
     */
    public boolean isHidden() {
        return this == CANCELLED || this == DRAFT || this == ANALYZING;
    }

    /** 사용자에게 노출되는 진행 중 상태 (목록 · 집계용) */
    public static List<AsStatus> visibleInProgressValues() {
        return Arrays.stream(values())
                .filter(s -> s.isInProgress() && !s.isHidden())
                .toList();
    }

    /** 목록 ALL 필터 대상 */
    public static List<AsStatus> visibleValues() {
        return Arrays.stream(values()).filter(s -> !s.isHidden()).toList();
    }

    /** filter=IN_PROGRESS 대상 */
    public boolean isInProgress() {
        return !terminal;
    }

    public static List<AsStatus> inProgressValues() {
        return Arrays.stream(values()).filter(AsStatus::isInProgress).toList();
    }
}
