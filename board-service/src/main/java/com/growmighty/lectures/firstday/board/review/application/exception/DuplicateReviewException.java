package com.growmighty.lectures.firstday.board.review.application.exception;

/**
 * 이미 같은 프로젝트에 활성(ACTIVE/MODIFIED) 리뷰를 작성한 사용자가 또 등록을 시도할 때 던진다.
 * IllegalStateException을 상속해 common의 GlobalExceptionHandler가 별도 등록 없이 409로 매핑하도록 한다.
 */
public class DuplicateReviewException extends IllegalStateException {
    public DuplicateReviewException(String message) {
        super(message);
    }
}