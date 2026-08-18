package com.ladder.mcmcare.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** API 명세서 부록 C 에러 코드 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    FUTURE_BIRTH_DATE(HttpStatus.BAD_REQUEST, "생년월일은 오늘 이후로 지정할 수 없습니다."),
    AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),
    PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "사진을 최소 1장 첨부해 주세요."),
    TOO_MANY_PHOTOS(HttpStatus.BAD_REQUEST, "사진은 최대 4장까지 첨부할 수 있습니다."),
    PHOTO_TYPE_REQUIRED(HttpStatus.BAD_REQUEST,
            "전체 제품 사진 1장과 손상 부위 사진을 최소 1장 이상 첨부해 주세요."),
    TOO_MANY_HANDOVER_PHOTOS(HttpStatus.BAD_REQUEST, "인계 사진은 최대 5장까지 첨부할 수 있습니다."),
    PAST_DATE(HttpStatus.BAD_REQUEST, "지난 날짜 또는 시간대는 선택할 수 없습니다."),
    SIGN_REQUIRED(HttpStatus.BAD_REQUEST, "서명이 필요합니다."),

    // 401
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일, 비밀번호를 다시 한번 확인해주세요."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

    // 403
    NO_PERMISSION(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    NO_MATCHING_DATA(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),

    // 409
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    SLOT_FULL(HttpStatus.CONFLICT, "선택하신 시간대는 예약이 마감되었습니다."),
    PICKUP_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 픽업 예약이 등록된 접수 건입니다."),
    ALREADY_ESTIMATED(HttpStatus.CONFLICT, "이미 견적이 산출된 접수 건입니다."),
    ALREADY_HANDED_OVER(HttpStatus.CONFLICT, "이미 인계가 완료된 건입니다."),
    NUMBER_CONFLICT(HttpStatus.CONFLICT, "요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 422
    CANCEL_DEADLINE_PASSED(HttpStatus.UNPROCESSABLE_ENTITY, "픽업 예정일 24시간 전까지만 취소할 수 있습니다."),
    INVALID_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "현재 상태에서는 처리할 수 없습니다."),
    NO_AVAILABLE_DRIVER(HttpStatus.UNPROCESSABLE_ENTITY, "배정 가능한 기사가 없습니다."),
    INVALID_PICKUP_DATE(HttpStatus.UNPROCESSABLE_ENTITY, "픽업 예정일이 아닌 건은 처리할 수 없습니다."),

    // 502
    ESTIMATE_FAILED(HttpStatus.BAD_GATEWAY, "견적 분석에 실패했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
