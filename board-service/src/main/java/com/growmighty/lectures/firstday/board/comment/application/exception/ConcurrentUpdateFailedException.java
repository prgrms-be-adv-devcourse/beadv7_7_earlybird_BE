package com.growmighty.lectures.firstday.board.comment.application.exception;

/**
 * 낙관적 락(@Version) 충돌이 재시도 횟수를 소진할 때까지 반복될 때 던진다.
 * IllegalStateException을 상속해 common의 GlobalExceptionHandler가 별도 등록 없이 409로 매핑하도록 한다.
 */
public class ConcurrentUpdateFailedException extends IllegalStateException {
    public ConcurrentUpdateFailedException(String message) {
        super(message);
    }
}