package com.growmighty.lectures.firstday.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * 프레임워크가 던지는 예외(요청 형식 오류, 404, 405, 클라이언트 연결 끊김 등)는
 * 부모인 ResponseEntityExceptionHandler가 RFC 9457 ProblemDetail로 알아서 처리한다.
 * 여기서는 스프링이 알 수 없는 것들만 직접 다룬다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (e.getCause() != null) {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
        }
        return problemOf(errorCode.getStatus(), errorCode, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[{}] {}", ErrorCode.INVALID_INPUT.getCode(), e.getMessage());
        return problemOf(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException e) {
        log.warn("[{}] {}", ErrorCode.INVALID_STATE.getCode(), e.getMessage());
        return problemOf(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, e.getMessage());
    }

    // 예상 못 한 예외. 내부 사정(e.getMessage())을 클라이언트에 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception e) {
        log.error("[{}] unhandled exception", ErrorCode.INTERNAL_ERROR.getCode(), e);
        return problemOf(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    // @Valid 실패: 기본 응답(detail: "Invalid request content.")에 필드별 오류 목록을 얹어 준다.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = ex.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        body.setProperty("code", ErrorCode.INVALID_INPUT.getCode());
        body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(FieldErrorDetail::from)
                .toList());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private ProblemDetail problemOf(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", errorCode.getCode());
        return problem;
    }

    private record FieldErrorDetail(String field, String message) {
        private static FieldErrorDetail from(FieldError fieldError) {
            return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
        }
    }
}
