# 회원(user-service) 테스트 현황 — 발표용 요약

## 요약

- 회원가입 / 로그인 / 토큰 갱신 / 로그아웃 / 내 정보 수정 / 비밀번호 변경 / 판매자(창작자) 전환까지, 회원 기능 전 구간에 자동화 테스트 적용
- 통합 테스트 도입 - **실제 DB, 실제 로그인 토큰**으로 동작을 검증

## 회원 기능 API 목록

| 기능 | API | 설명 | 컨트롤러 | 서비스 로직 |
| --- | --- | --- | --- | --- |
| 회원가입 | `POST /api/v1/users/signup` | 이메일·비밀번호·이름·전화번호로 신규 가입 | [`UserController.signup`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L29) | [`UserService.register`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L27) |
| 로그인 | `POST /api/v1/users/login` | 이메일·비밀번호 인증 후 접속 토큰(access) + 재로그인용 토큰(refresh) 발급 | [`UserController.login`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L34) | [`UserService.authenticate`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L37) |
| 토큰 갱신 | `POST /api/v1/users/refresh` | 재로그인용 토큰으로 접속 토큰만 다시 발급 (앱을 다시 열 때 자동 로그인 유지) | [`UserController.refresh`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L43) | [`UserService.getUser`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L47) |
| 로그아웃 | `POST /api/v1/users/logout` | 보유 토큰 유효성 확인 후 로그아웃 처리 | [`UserController.logout`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L57) | — |
| 내 정보 조회 | `GET /api/v1/users/me` | 로그인한 내 정보 조회 | [`UserController.getMe`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L63) | [`UserService.getUser`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L47) |
| 내 정보 수정 | `PATCH /api/v1/users/me` | 이름·전화번호 수정 | [`UserController.updateMe`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L70) | [`UserService.updateProfile`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L54) |
| 비밀번호 변경 | `PATCH /api/v1/users/me/password` | 현재 비밀번호 확인 후 새 비밀번호로 변경 | [`UserController.changePassword`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L86) | [`UserService.changePassword`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L76) |
| 판매자(창작자) 등록 | `POST /api/v1/users/me/creator` | 정산 계좌 등록 + 후원자→창작자로 전환 | [`UserController.registerAsCreator`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/UserController.java#L78) | [`UserService.registerAsCreator`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L63) |
| (내부) 회원 조회 | `GET /internal/v1/users/{id}` | 주문·프로젝트 등 다른 서비스가 회원 정보를 조회할 때 사용 (외부에서는 접근 불가) | [`InternalUserController.getUser`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/presentation/InternalUserController.java#L16) | [`UserService.getUser`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L47) |

핵심 도메인 규칙: [`User`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/domain/User.java) (가입/역할전환/비밀번호변경 가드), [`CreatorProfile`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/domain/CreatorProfile.java) (정산 계좌 검증)

## 검증한 흐름 (시나리오)

**정상 흐름 한 번에 검증** — 실제로 이렇게 이어지는지 확인 ([`UserFlowIntegrationTest`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/presentation/UserFlowIntegrationTest.java) 전체):

```
회원가입 → 로그인(토큰 발급)                                    [signupThenLogin_issuesTokensAndPersistsHashedPassword]
→ 내 정보 수정 → 비밀번호 변경                                   [updateProfileThenChangePassword_reflectsInSubsequentLogin]
→ (이전 비밀번호로는 로그인 실패 / 새 비밀번호로는 로그인 성공)      [updateProfileThenChangePassword_reflectsInSubsequentLogin]
→ 판매자 등록(창작자 전환) → 재등록 시도 시 차단                   [registerAsCreator_thenDuplicateRegistration_returns409]
```

- [`signupThenLogin_issuesTokensAndPersistsHashedPassword`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/presentation/UserFlowIntegrationTest.java#L63)
- [`updateProfileThenChangePassword_reflectsInSubsequentLogin`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/presentation/UserFlowIntegrationTest.java#L131)
- [`registerAsCreator_thenDuplicateRegistration_returns409`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/presentation/UserFlowIntegrationTest.java#L175)

**함께 검증한 주요 실패 케이스**

- 이미 가입된 이메일로 재가입 시도 → 차단 ([`UserService.register`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L28))
- 잘못된 비밀번호로 로그인 시도 → 차단 ([`UserService.authenticate`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L40))
- 이미 판매자로 등록된 회원이 중복 등록 시도 → 차단 ([`UserService.registerAsCreator`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/application/UserService.java#L66), [`User.becomeCreator`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/domain/User.java#L52))
- 만료·위조된 토큰으로 접근 시도 → 차단 ([`JwtTokenProvider`](../../user-service/src/main/java/com/growmighty/lectures/firstday/user/infrastructure/JwtTokenProvider.java))
- 필수 입력값 누락(빈 이메일/비밀번호 등) → 차단 (요청 DTO의 `@NotBlank`/`@Email` 검증)

**저장 안정성 검증**

- 비밀번호는 평문이 아닌 암호화된 값으로 저장되는지 확인 ([`UserFlowIntegrationTest.signupThenLogin_issuesTokensAndPersistsHashedPassword`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/presentation/UserFlowIntegrationTest.java))
- 이메일 중복, 판매자 정산 계좌 중복 저장이 DB 차원에서도 실제로 막히는지 확인 — 서비스 로직이 우회되는 극단 상황 대비 ([`UserPersistenceTest`](../../user-service/src/test/java/com/growmighty/lectures/firstday/user/infrastructure/UserPersistenceTest.java))

## 참고

- 실행: `./gradlew :user-service:test --parallel` (Docker 필요)
