package com.growmighty.lectures.firstday.common.exception;

public class ServiceUnavailableException extends BusinessException {
    public ServiceUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
