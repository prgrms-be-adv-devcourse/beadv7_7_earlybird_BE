package com.growmighty.lectures.firstday.user.domain;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("이메일이 비어 있으면 가입할 수 없다")
    void register_withBlankEmail_throws() {
        assertThatThrownBy(() -> User.register("", "encoded", "김하나한", "010-0000-0000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일이 null 이면 가입할 수 없다")
    void register_withNullEmail_throws() {
        assertThatThrownBy(() -> User.register(null, "encoded", "김하나한", "010-0000-0000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가입한 유저의 기본 role 은 BACKER 다")
    void register_defaultsToBackerRole() {
        User user = User.register("hana@example.com", "encoded", "김하나한", "010-0000-0000");

        assertThat(user.getRole()).isEqualTo(UserRole.BACKER);
    }

    @Test
    @DisplayName("이미 창작자인 유저를 다시 창작자로 등록하면 예외가 발생한다")
    void becomeCreator_whenAlreadyCreator_throws() {
        User user = User.register("hana@example.com", "encoded", "김하나한", "010-0000-0000");
        user.becomeCreator();

        assertThatThrownBy(user::becomeCreator).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("창작자로 등록하면 role 이 CREATOR 로 바뀐다")
    void becomeCreator_switchesRoleToCreator() {
        User user = User.register("hana@example.com", "encoded", "김하나한", "010-0000-0000");

        user.becomeCreator();

        assertThat(user.getRole()).isEqualTo(UserRole.CREATOR);
    }

    @Test
    @DisplayName("새 비밀번호가 비어 있으면 변경할 수 없다")
    void changePassword_withBlankNewPassword_throws() {
        User user = User.register("hana@example.com", "encoded-old", "김하나한", "010-0000-0000");

        assertThatThrownBy(() -> user.changePassword(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    @DisplayName("새 비밀번호가 null 이면 변경할 수 없다")
    void changePassword_withNullNewPassword_throws() {
        User user = User.register("hana@example.com", "encoded-old", "김하나한", "010-0000-0000");

        assertThatThrownBy(() -> user.changePassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호를 변경하면 새 값으로 대체된다")
    void changePassword_replacesPassword() {
        User user = User.register("hana@example.com", "encoded-old", "김하나한", "010-0000-0000");

        user.changePassword("encoded-new");

        assertThat(user.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    @DisplayName("프로필을 수정하면 이름과 전화번호가 갱신된다")
    void updateProfile_updatesNameAndPhoneNumber() {
        User user = User.register("hana@example.com", "encoded", "옛이름", "010-0000-0000");

        user.updateProfile("새이름", "010-1111-1111");

        assertThat(user.getName()).isEqualTo("새이름");
        assertThat(user.getPhoneNumber()).isEqualTo("010-1111-1111");
    }
}
