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
 * 컨트롤러가 반환한 DTO를 {@link ApiResponse#ok}로 {success: true, data, error: null} 봉투에 담는다.
 * 실패 응답은 {@link com.growmighty.lectures.firstday.common.exception.GlobalExceptionHandler}가
 * {@link ApiResponse#fail}로 직접 만들어 반환하므로 여기서는 별도 변환이 필요 없다 — 이미 {@link ApiResponse}인
 * 경우 그대로 흘려보낼 뿐이다.
 * ProblemDetail 을 추가로 흘려보내는 건, GlobalExceptionHandler의 손이 닿지 않는 곳(다른 모듈의
 * @ControllerAdvice 등)에서 ProblemDetail 이 새어 나오더라도 success: true 로 잘못 감싸지 않기 위한
 * 방어 코드일 뿐이다.
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
 * <p>basePackages를 우리 코드로 한정한다 — 그렇지 않으면 springdoc(OpenAPI) 같은 서드파티 컨트롤러의
 * 응답(예: {@code byte[]}로 선언된 {@code /v3/api-docs})까지 감싸버려서, 이미 선택된
 * {@link HttpMessageConverter}가 엉뚱한 타입을 받아 캐스팅에 실패한다.
 */
@RestControllerAdvice(basePackages = "com.growmighty.lectures.firstday")
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
