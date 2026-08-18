package com.ladder.mcmcare.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 실패 응답에 함께 내려줄 부가 정보 (예: 견적 실패 시 asNo) */
    private final String asNo;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, String asNo) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.asNo = asNo;
    }
}
