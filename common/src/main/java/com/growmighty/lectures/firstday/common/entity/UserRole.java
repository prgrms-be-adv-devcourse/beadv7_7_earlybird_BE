package com.growmighty.lectures.firstday.common.entity;

import lombok.Getter;
import lombok.NonNull;

/** BACKER: 후원자(기본값) / CREATOR: 판매자 등록 완료 / ADMIN: 관리자. CREATOR/ADMIN 도 후원 기능을 전부 쓸 수 있다. */
@Getter
public enum UserRole {
    /** 후원자(기본값)*/
    BACKER("BACKER"),
    /** 창작자 */
    CREATOR("CREATOR"),
    /** 관리자 */
    ADMIN("ADMIN");

    private final String code;

    UserRole(@NonNull final String code) {
        this.code = code;
    }
}
