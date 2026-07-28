# 게이트웨이(gateway-server) 테스트 현황 — 발표용 요약

## 요약

- 모든 외부 요청이 거쳐가는 단일 진입점 — 로그인 토큰(JWT) 검증, 역할(BACKER/CREATOR/ADMIN)별 접근 제어, 검증된 사용자 정보를 내부 서비스로 전달
- 통합 테스트 도입 - **실제로 발급한 JWT** 로 전체 라우트(역할 제한 라우트 24개 포함) 동작을 검증

## 하는 일

| 역할 | 설명 | 코드 |
| --- | --- | --- |
| 인증(로그인 여부) 검증 | Authorization 헤더의 JWT 를 검증 — 없거나 만료·위조된 토큰은 차단 | [`SecurityConfig.jwtDecoder`](../../gateway-server/src/main/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfig.java#L111) |
| 권한(역할별) 접근 제어 | 라우트마다 필요한 역할(BACKER/CREATOR/ADMIN)이 다름 — 나머지는 로그인만 하면 통과 | [`SecurityConfig.filterChain`](../../gateway-server/src/main/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfig.java#L36) |
| 사용자 정보 전달 | 검증된 JWT 의 subject/role 을 `X-User-Id`/`X-User-Role` 헤더로 변환해 내부 서비스에 전달 — 클라이언트가 이 헤더를 위조해 보내도 항상 무시 | [`UserHeaderForwardingFilter`](../../gateway-server/src/main/java/com/growmighty/lectures/firstday/gateway/security/UserHeaderForwardingFilter.java#L29) |

## 검증한 항목

### 인증 검증 ([`SecurityConfigTest`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java))

- 토큰 없이 보호된 경로 호출 → 401 ([`protectedPath_withoutToken_isUnauthorized`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L52))
- 만료된 토큰으로 호출 → 401 ([`protectedPath_withExpiredToken_isUnauthorized`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L72))
- 유효한 토큰으로 호출 → 보안 계층 통과 ([`protectedPath_withValidToken_passesSecurityLayer`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L60))
- 로그인/회원가입, PG 웹훅, 프로젝트·리워드 조회 등 공개 경로는 토큰 없이도 통과 ([`publicPaths_withoutToken_areNotRejectedBySecurityLayer`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L83), [`projectReadPaths_withoutToken_areNotRejectedBySecurityLayer`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L92))

### 권한(역할별) 검증 ([`SecurityConfigTest`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java))

```
역할 제한 라우트 24개 전수 검사                          [roleGatedRoute_withAllowedRole_passesSecurityLayer]
→ 허용된 role(CREATOR/ADMIN)은 통과, BACKER는 403        [roleGatedRoute_withBackerToken_isForbidden]
```

- [`roleGatedRoute_withAllowedRole_passesSecurityLayer`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L266) / [`roleGatedRoute_withBackerToken_isForbidden`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L280) — 새 라우트가 추가되면서 보안 설정을 깜빡 빠뜨리는 회귀를 잡기 위한 전수 검사
- 와일드카드 경로보다 특정 규칙이 먼저 매칭되는지 확인: `/projects/me`(CREATOR 전용)가 `/projects/*`(공개) 와일드카드에 묻히지 않음 ([`projectsMe_withBackerToken_isForbidden`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L142)), `/settlements/all`(ADMIN 전용)도 `/settlements/*`(CREATOR)에 묻히지 않음 ([`settlementsAll_withCreatorToken_isForbidden`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/config/SecurityConfigTest.java#L153))

### 사용자 정보 전달 검증 ([`UserHeaderForwardingFilterTest`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/security/UserHeaderForwardingFilterTest.java))

- 인증된 요청의 JWT subject/role 이 `X-User-Id`/`X-User-Role` 헤더로 다운스트림에 전달됨 ([`authenticatedRequest_forwardsTrustedHeaders`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/security/UserHeaderForwardingFilterTest.java#L27))
- 클라이언트가 두 헤더를 위조해도 검증된 값으로 덮어써짐 ([`authenticatedRequest_stripsSpoofedHeaderBeforeSettingVerifiedValue`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/security/UserHeaderForwardingFilterTest.java#L46))
- 인증 정보 없는 공개 경로 요청은 위조 헤더만 제거된 채 그대로 진행 ([`unauthenticatedRequest_stripsHeadersAndContinuesChain`](../../gateway-server/src/test/java/com/growmighty/lectures/firstday/gateway/security/UserHeaderForwardingFilterTest.java#L67))

## 참고

- 실행: `./gradlew :gateway-server:test --parallel`
