package com.growmighty.lectures.firstday.user.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phoneNumber;

    /** 단일 유저 도메인을 역할로 구분한다. CREATOR/ADMIN 도 후원(구매) 기능을 전부 쓸 수 있다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private User(String email, String encodedPassword, String name, String phoneNumber) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.role = UserRole.USER;
    }

    public static User register(String email, String encodedPassword, String name, String phoneNumber) {
        return new User(email, encodedPassword, name, phoneNumber);
    }

    /** 판매자(창작자) 등록 — role 전환. creator_profiles 생성은 애플리케이션 서비스에서 함께 처리한다. */
    public void becomeCreator() {
        if (this.role == UserRole.CREATOR) {
            throw new IllegalStateException("이미 창작자로 등록되어 있습니다.");
        }
        this.role = UserRole.CREATOR;
    }

    public void changePassword(String newEncodedPassword) {
        if (newEncodedPassword == null || newEncodedPassword.isBlank()) {
            throw new IllegalArgumentException("새 비밀번호는 비어 있을 수 없습니다.");
        }
        this.password = newEncodedPassword;
    }

    public void updateProfile(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }
}
