package com.growmighty.lectures.firstday.common.exception;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * 모든 예외를 {@link ApiResponse#fail}의 {success: false, data: null, error} 봉투로 응답한다.
 * 우리가 직접 던진 예외(BusinessException 등)는 아래 @ExceptionHandler들이 바로 처리하고,
 * 프레임워크가 우리 핸들러에 닿기도 전에 자체 처리하는 예외(요청 형식 오류, 404, 405 등)는
 * 부모인 ResponseEntityExceptionHandler가 기본 ProblemDetail을 만든 뒤 {@link #handleExceptionInternal}로
 * 넘기므로, 그 지점에서 한 번만 같은 봉투로 변환한다.
 *
 * <p>커스텀 예외 응답 예시 ({@link BusinessException}으로 존재하지 않는 주문을 조회한 경우, HTTP 상태 404):
 * <pre>{@code
 * {
 *   "success": false,
 *   "data": null,
 *   "error": { "message": "존재하지 않는 주문입니다. orderId=999", "errors": null }
 * }
 * }</pre>
 *
 * <p>@Valid 검증 실패 응답 예시 (HTTP 상태 400):
 * <pre>{@code
 * {
 *   "success": false,
 *   "data": null,
 *   "error": {
 *     "message": "Invalid request content.",
 *     "errors": [ { "field": "userId", "message": "must not be null" } ]
 *   }
 * }
 * }</pre>
 *
 * <p>성공 응답은 이 클래스가 아니라
 * {@link com.growmighty.lectures.firstday.common.response.ApiResponseWrappingAdvice}가 만든다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getStatus();
        if (e.getCause() != null) {
            log.warn("[{}] {}", status.value(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", status.value(), e.getMessage());
        }
        return fail(status, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[{}] {}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return fail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.warn("[{}] {}", HttpStatus.CONFLICT.value(), e.getMessage());
        return fail(HttpStatus.CONFLICT, e.getMessage());
    }

    // 예상 못 한 예외. 내부 사정(e.getMessage())을 클라이언트에 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[{}] unhandled exception", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    // @Valid 실패: 기본 메시지("Invalid request content.")에 필드별 오류 목록을 담아 준다.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiResponse.ApiError.FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.ApiError.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiResponse<Void> body = ApiResponse.fail(new ApiResponse.ApiError("Invalid request content.", errors));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // 우리 핸들러에 닿지 않는 프레임워크 기본 처리(요청 형식 오류, 404, 405 등)는 ProblemDetail을 만들어
    // 여기로 넘어온다 — 표준 필드(detail)만으로 같은 봉투로 변환한다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                              HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            body = ApiResponse.fail(new ApiResponse.ApiError(problem.getDetail(), null));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(new ApiResponse.ApiError(message, null)));
    }
}
