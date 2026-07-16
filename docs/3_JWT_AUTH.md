# JWT 인증 가이드

## 한 줄 요약

**gateway-server 만 JWT 를 검증한다.** 다운스트림 서비스(user, order, cart, ...)는 JWT 라이브러리도
서명 키도 모른다 — 게이트웨이가 검증을 마친 뒤 넣어주는 `X-User-Id` / `X-User-Role` 헤더만 읽는다.
클라이언트가 이 두 헤더를 직접 보내도 게이트웨이가 먼저 제거하므로 위조할 수 없다.

## 1. 토큰 발급 — `POST /users/login`

```http
POST http://localhost:8000/users/login
Content-Type: application/json

{
  "email": "buyer@growmighty.co.kr",
  "password": "rawPassword1!"
}
```

응답:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 1, "email": "buyer@growmighty.co.kr", "name": "구매자", "phoneNumber": "010-1111-1111", "role": "USER" }
}
```

`POST /users/signup` 은 토큰을 발급하지 않는다 — 가입 후 `/users/login` 을 따로 호출한다.

## 2. 보호된 엔드포인트 호출

```http
GET http://localhost:8000/users/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

`Authorization` 헤더가 없거나, 토큰이 만료·위조됐으면 게이트웨이가 바로 `401` 을 반환한다
(요청이 다운스트림 서비스까지 가지도 않는다).

## 3. 공개(인증 불필요) 엔드포인트

`gateway-server`의 `SecurityConfig.filterChain()` 에 `permitAll` 로 등록되어 있다:

- `POST /users/signup`
- `POST /users/login`

새 공개 API 를 추가하려면 이 목록(`.pathMatchers(HttpMethod.POST, "/users/signup", "/users/login").permitAll()`)에 함께 추가해야 한다.
빼먹으면 기본값(`.anyExchange().authenticated()`)에 걸려 `401` 이 난다.

## 4. 팀원이 자기 서비스에 도입할 때 — 패턴

user-service 의 `/users/me`, `/users/me/creator` 가 이미 이 패턴으로 마이그레이션되어 있다 — 그대로 복사해서 쓰면 된다.

**Before**
```java
@GetMapping("/me")
public XxxResponse getMe(@RequestParam Long userId) { ... }
```

**After**
```java
@GetMapping("/me")
public XxxResponse getMe(@RequestHeader(JwtHeaders.USER_ID) Long userId) { ... }
```

`JwtHeaders` 는 `common` 모듈(`com.growmighty.lectures.firstday.common.jwt.JwtHeaders`)에 있다 —
서비스 모듈은 이미 `implementation project(':common')` 을 갖고 있으므로 import 만 추가하면 된다.
역할(role)이 필요하면 같은 방식으로 `@RequestHeader(JwtHeaders.USER_ROLE) String role` 을 추가한다.

**왜 안전한가**: 게이트웨이의 `UserHeaderForwardingFilter` 가 인바운드 요청의 `X-User-Id`/`X-User-Role` 을
**항상 먼저 제거한 뒤**, JWT 검증에 성공한 경우에만 검증된 값으로 다시 채운다. 클라이언트가 이 헤더를
직접 보내도 게이트웨이를 통과하는 순간 사라진다.

**주의**: 서비스를 게이트웨이 없이 직접(예: `:8083`) 호출하면 이 헤더 자체가 없다.
로컬에서 게이트웨이 없이 단독 테스트하려면 Postman 등으로 헤더를 직접 넣어 우회할 수 있다 —
`@RequestParam` 방식과 신뢰 수준은 동일하다(로컬 편의용, 프로덕션 경로 아님).

## 5. 비밀 키

`beadv7_7_earlybird_config/application.yml` 의 `jwt.secret` / `jwt.access-token-expiration-seconds` —
이 파일은 서비스 이름과 무관하게 **모든** 서비스에 배달되지만, 실제로 읽는 건 user-service(발급)와
gateway-server(검증) 뿐이다. 값을 바꾸고 재기동 없이 반영하려면 두 서비스 모두 `/actuator/refresh` 호출.

로컬 개발용 값이 이미 들어 있다. 새로 생성하려면:
```bash
openssl rand -base64 32
```

## 6. 왜 `common/build.gradle` 이 `api` → `implementation` 으로 바뀌었나

JWT 설정(`JwtProperties`)과 헤더/클레임 이름 상수(`JwtHeaders`)는 `common` 모듈에 있다 — 발급 쪽과
검증 쪽이 같은 이름을 쓰도록 강제하기 위해서다. 그런데 `gateway-server` 는 WebFlux 기반, DB 도 없는
서비스라 `common` 이 전파하던 `spring-boot-starter-webmvc`/`data-jpa` 를 그대로 받으면 (Servlet+Reactive
스택 혼재, DB 없이 JPA 자동 설정 시도로 기동 실패) 문제가 생긴다. 그래서 이 세 의존성을 `api` 에서
`implementation` 으로 낮췄다 — 기존 서비스는 전부 이 셋을 자기 `build.gradle` 에 이미 직접 선언하고 있어
영향이 없고, `gateway-server` 는 이제 `common` 에 의존해도 필요한 것만 받는다.
**실수로 되돌리지 말 것** — 원래 `api` 였다고 착각하고 복구하면 gateway 기동이 다시 깨질 수 있다.

## 범위 밖 (다음 단계)

- Refresh token, 토큰 폐기/로테이션
- `X-User-Role` 을 이용한 `/admin/**` 전용 권한 검사 (project-service 의 기존 TODO)
- order/cart/payment/board/project/settlement/notification-service 의 `@RequestParam userId` 마이그레이션 —
  각 서비스 오너가 §4 패턴을 그대로 따라 하면 된다.
