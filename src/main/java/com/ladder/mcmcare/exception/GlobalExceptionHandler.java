package com.ladder.mcmcare.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException: {} - {}", code.name(), e.getMessage());
        return ResponseEntity
                .status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage(), e.getAsNo()));
    }

    /** @Valid 실패 → 첫 번째 필드 메시지를 노출 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String message = (first != null && first.getDefaultMessage() != null)
                ? first.getDefaultMessage()
                : ErrorCode.VALIDATION_FAILED.getMessage();
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, message));
    }

    /**
     * multipart part 자체가 누락된 경우.
     *
     * @RequestPart 는 Spring 단계에서 필수라, part 가 없으면 서비스의 검증에 도달하지 못한다.
     * 그대로 두면 전역 catch-all 에 걸려 500 이 나가므로 여기서 도메인 에러 코드로 변환한다.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {

        ErrorCode code = switch (e.getRequestPartName()) {
            case "images", "photos" -> ErrorCode.PHOTO_REQUIRED;
            case "customerSign", "driverSign" -> ErrorCode.SIGN_REQUIRED;
            default -> ErrorCode.VALIDATION_FAILED;
        };

        log.warn("multipart part 누락: {}", e.getRequestPartName());
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "첨부 파일 용량이 너무 큽니다."));
    }

    /** @PreAuthorize 거부 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
                .status(ErrorCode.NO_PERMISSION.getStatus())
                .body(ErrorResponse.of(ErrorCode.NO_PERMISSION));
    }

    /**
     * 매핑되지 않은 경로.
     *
     * 그냥 두면 아래 전역 catch 에 걸려 500 이 나간다.
     * 500 은 "서버가 고장났다"는 뜻이라 클라이언트가 재시도할지 판단할 수 없다.
     * 404 면 "그런 주소는 없다"가 명확해 호출 코드를 고치면 된다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로: {}", e.getResourcePath());
        return response(ErrorCode.NO_MATCHING_DATA);
    }

    /**
     * 본문을 읽지 못한 경우 — JSON 문법 오류, 정의되지 않은 enum 값, 잘못된 날짜 형식 등.
     * 전부 사용자 입력 문제이므로 400 이 맞다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 해석 실패: {}", e.getMostSpecificCause().getMessage());
        return response(ErrorCode.VALIDATION_FAILED);
    }

    /**
     * 경로 변수 · 쿼리 파라미터의 타입이 맞지 않는 경우.
     * 예) /api/pickup/slot?startDate=abc  →  LocalDate 로 변환 실패
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("파라미터 타입 불일치: {} = {}", e.getName(), e.getValue());
        return response(ErrorCode.VALIDATION_FAILED);
    }

    /** 필수 쿼리 파라미터 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("필수 파라미터 누락: {}", e.getParameterName());
        return response(ErrorCode.VALIDATION_FAILED);
    }

    /** 경로는 맞지만 HTTP 메서드가 다른 경우 (예: GET 으로 만든 API 를 POST 로 호출) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 메서드: {}", e.getMethod());
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    /**
     * 위에서 걸러지지 않은 예외만 여기 온다.
     * 여기 걸리는 것은 진짜 서버 결함이므로 로그를 남기고 조사해야 한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.builder()
                        .code("INTERNAL_ERROR")
                        .message("일시적인 오류가 발생했습니다.")
                        .build());
    }
}
