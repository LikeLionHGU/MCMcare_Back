package com.ladder.mcmcare.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
