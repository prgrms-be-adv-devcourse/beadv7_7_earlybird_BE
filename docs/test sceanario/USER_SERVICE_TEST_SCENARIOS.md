# user-service 테스트 시나리오

`user-service`의 테스트를 3단계로 나눠 관리한다. 자세한 배경은 각 절 참고.

| 단계 | 언제 쓰나 | 이 서비스에서의 예 |
| --- | --- | --- |
| 단위 테스트 | 순수 로직(도메인/서비스)에 프레임워크 개입이 없을 때 | `UserTest`, `CreatorProfileTest`, `UserServiceTest` |
| 슬라이스 테스트 | 검증/예외 매핑처럼 스프링 자체의 동작을 확인해야 할 때 | `UserControllerTest`, `InternalUserControllerTest` |
| 통합 테스트 | 계층이 실제로 이어졌을 때만 드러나는 보장(암호화 저장, 토큰 디코딩, DB 제약)을 확인할 때 | `UserFlowIntegrationTest`, `UserPersistenceTest` |

## 단위 테스트

### `UserServiceTest` — `application/UserServiceTest.java`
Repository/PasswordEncoder를 목(mock)으로 대체해 `UserService`의 각 분기를 검증한다.

- [x] `register` — 이미 가입된 이메일이면 예외
- [x] `register` — 비밀번호를 인코딩한 뒤 저장
- [x] `authenticate` — 존재하지 않는 이메일이면 예외
- [x] `authenticate` — 비밀번호가 틀리면 예외
- [x] `authenticate` — 정상이면 사용자 정보 반환
- [x] `getUser` — 존재하지 않는 유저 조회 시 예외
- [x] `updateProfile` — 존재하지 않는 유저면 예외
- [x] `updateProfile` — 이름/전화번호 갱신
- [x] `registerAsCreator` — 존재하지 않는 유저면 예외
- [x] `registerAsCreator` — 이미 판매자로 등록돼 있으면 예외
- [x] `registerAsCreator` — role이 CREATOR로 바뀌고 정산 계좌 정보 저장
- [x] `changePassword` — 존재하지 않는 유저면 예외
- [x] `changePassword` — 현재 비밀번호가 틀리면 예외
- [x] `changePassword` — 새 비밀번호를 인코딩한 뒤 저장

### `UserTest` — `domain/UserTest.java`
- [x] `register` — 이메일이 비어있거나(blank) null이면 예외
- [x] `register` — 기본 role은 BACKER
- [x] `becomeCreator` — 이미 CREATOR면 예외
- [x] `becomeCreator` — role이 CREATOR로 전환
- [x] `changePassword` — 새 비밀번호가 비어있거나 null이면 예외
- [x] `changePassword` — 정상 교체
- [x] `updateProfile` — 이름/전화번호 갱신

### `CreatorProfileTest` — `domain/CreatorProfileTest.java`
- [x] `register` — userId가 null이면 예외
- [x] `register` — 은행명/계좌번호/예금주명이 비어있으면 각각 예외
- [x] `register` — 정상 필드 저장
- [x] `updateAccount` — 정상 교체

## 슬라이스 테스트 (`@WebMvcTest`)

### `UserControllerTest` — `presentation/UserControllerTest.java`
- [x] `POST /signup` — 필수 필드 blank → 400
- [x] `POST /login` — email blank/형식 오류 → 400
- [x] `POST /login` — 정상 → access/refresh 토큰 반환
- [x] `POST /refresh` — refreshToken blank → 400
- [x] `POST /refresh` — 유효한 토큰 → 새 access token
- [x] `POST /refresh` — 유효하지 않은 토큰 → 401
- [x] `POST /logout` — 유효한 토큰 → 204
- [x] `POST /logout` — 유효하지 않은 토큰 → 401
- [x] `GET /me` — 현재 사용자 정보 반환
- [x] `POST /me/creator` — 필수 필드 blank → 400
- [x] `POST /me/creator` — 정상 → CREATOR로 전환된 사용자 반환
- [x] `PATCH /me/password` — 필수 필드 blank → 400
- [x] `PATCH /me/password` — 현재 비밀번호 틀림 → 400
- [x] `PATCH /me/password` — 정상 → 204
- [x] `PATCH /me` — 필수 필드 blank → 400
- [x] `PATCH /me` — 정상 → 갱신된 정보 반환

### `InternalUserControllerTest` — `presentation/InternalUserControllerTest.java`
- [x] `GET /internal/v1/users/{userId}` — 정상 조회
- [x] `GET /internal/v1/users/{userId}` — 존재하지 않으면 404

## 통합 테스트 (`@SpringBootTest` + Testcontainers MySQL)

### `UserFlowIntegrationTest` — `presentation/UserFlowIntegrationTest.java`
실제 DB + 실제 JWT 발급/검증으로 계층을 관통하는 happy path와, 그 경로에서만 드러나는 부작용을 확인한다.

- [x] 회원가입 → 로그인 시 토큰 발급, 비밀번호는 평문이 아닌 해시로 저장됨
- [x] 이미 가입된 이메일로 회원가입 → 409
- [x] 틀린 비밀번호로 로그인 → 400
- [x] 로그인 → 리프레시 토큰으로 새 access token 발급, 같은 사용자를 가리킴(subject 일치)
- [x] 내 정보 수정 → 비밀번호 변경 → 이후 로그인은 새 비밀번호로만 성공
- [x] 판매자 등록 → role이 CREATOR로 전환, 재등록 시 409
- [x] internal API로 경로 변수만으로 사용자 정보 조회

### `UserPersistenceTest` — `infrastructure/UserPersistenceTest.java`
서비스 계층의 중복 검사(`existsByEmail`/`existsByUserId`)를 우회하는 경합이 생겨도 DB 유니크 제약이 최후의 방어선으로 작동하는지 확인한다.

- [x] `users.email` 중복 저장 → `DataIntegrityViolationException`
- [x] `creator_profiles.user_id` 중복 저장 → `DataIntegrityViolationException`

## 실행

```bash
docker compose -f infrastructure/docker-compose.yml up -d mysql   # Testcontainers는 별도 컨테이너를 띄우므로 필수는 아니지만 Docker 데몬은 떠 있어야 함
./gradlew :user-service:test --parallel
```
