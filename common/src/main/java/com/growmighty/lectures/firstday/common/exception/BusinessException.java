package com.growmighty.lectures.firstday.common.exception;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(@NonNull HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
