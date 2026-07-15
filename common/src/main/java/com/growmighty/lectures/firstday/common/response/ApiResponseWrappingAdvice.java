package com.growmighty.lectures.firstday.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 컨트롤러가 반환한 DTO를 {success, data, error} 봉투로 감싼다.
 * 에러는 GlobalExceptionHandler가 ProblemDetail로 별도 처리하므로 여기서는 그대로 흘려보낸다.
 * ProblemDetail 은 ResponseEntity<Object> 로 감싸져 넘어올 수 있어 선언된 returnType 만으로는
 * 걸러낼 수 없다 — beforeBodyWrite 시점의 실제 body 타입으로 판단해야 한다.
 *
 * <p>성공 응답 예시 (컨트롤러는 {@code UserResponse}만 반환하면 된다):
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { "id": 1, "email": "hana@example.com" },
 *   "error": null
 * }
 * }</pre>
 *
 * <p>실패 응답은 이 클래스가 아니라 {@link com.growmighty.lectures.firstday.common.exception.GlobalExceptionHandler}가
 * RFC 9457 {@link ProblemDetail}로 만든다.
 */
@RestControllerAdvice
public class ApiResponseWrappingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> || body instanceof ProblemDetail) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
