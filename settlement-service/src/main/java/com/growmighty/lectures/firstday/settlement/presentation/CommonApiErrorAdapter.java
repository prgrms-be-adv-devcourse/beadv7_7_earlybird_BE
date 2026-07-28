package com.growmighty.lectures.firstday.settlement.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

/**
 * 서로 다른 common 기준선의 ApiError 생성자 형태를 Settlement 내부에서 흡수한다.
 *
 * <p>프로젝트 정산 브랜치의 common은 (code, message, errors), develop/common은
 * (message, errors)를 사용한다. develop과 common을 수정하거나 브랜치를 병합하지 않고도
 * Settlement 오류 응답이 두 계약에서 컴파일되도록 레코드 구성요소 이름으로 값을 조립한다.
 */
public final class CommonApiErrorAdapter {

    private CommonApiErrorAdapter() {
    }

    public static ApiResponse.ApiError withoutCode(String message) {
        RecordComponent[] components = ApiResponse.ApiError.class.getRecordComponents();
        Constructor<ApiResponse.ApiError> constructor = canonicalConstructor(components);
        Object[] arguments = Arrays.stream(components)
                .map(component -> argumentFor(component, message))
                .toArray();

        try {
            return constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("공통 API 오류 응답을 생성할 수 없습니다.", exception);
        }
    }

    private static Constructor<ApiResponse.ApiError> canonicalConstructor(RecordComponent[] components) {
        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);

        try {
            return ApiResponse.ApiError.class.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("공통 API 오류 응답 계약을 확인할 수 없습니다.", exception);
        }
    }

    private static Object argumentFor(RecordComponent component, String message) {
        return switch (component.getName()) {
            case "code", "errors" -> null;
            case "message" -> message;
            default -> throw new IllegalStateException(
                    "지원하지 않는 공통 API 오류 필드입니다: " + component.getName()
            );
        };
    }
}
