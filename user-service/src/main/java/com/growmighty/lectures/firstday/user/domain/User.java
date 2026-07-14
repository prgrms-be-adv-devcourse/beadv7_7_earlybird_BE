package com.growmighty.lectures.firstday.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
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

    private User(String email, String encodedPassword, String name, String phoneNumber) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public static User register(String email, String encodedPassword, String name, String phoneNumber) {
        return new User(email, encodedPassword, name, phoneNumber);
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
