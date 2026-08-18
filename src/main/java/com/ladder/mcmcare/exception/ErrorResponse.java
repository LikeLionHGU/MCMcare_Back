package com.ladder.mcmcare.exception;

import lombok.Builder;
import lombok.Getter;

/**
 * 실패 응답 본문.
 * 성공 응답은 각 도메인 DTO를 그대로 반환한다 (래핑하지 않는다).
 */
@Getter
@Builder
public class ErrorResponse {

    private String code;
    private String message;

    /** 견적 실패 시 asNo. null 이면 직렬화되지 않는다. */
    private String asNo;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String asNo) {
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(message)
                .asNo(asNo)
                .build();
    }
}
